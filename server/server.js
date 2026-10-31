const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const crypto = require('crypto');

const args = process.argv.slice(2);
let port = 3000;
let isOfficial = false;
let ngrokToken = null;
let tunnelAddress = null;
const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'preferences.json'), 'utf8'));
const VERCEL_API_URL = "https://server-list-orpin.vercel.app/api";

for (let i = 0; i < args.length; i++) {
    if (args[i] === '-port' && args[i + 1]) {
        port = parseInt(args[i + 1], 10);
    }
    if (args[i] === '-official') {
        if (args[i + 1] === 'false' || args[i + 1] === '0') {
            isOfficial = false;
        } else {
            isOfficial = true;
        }
    }
    if (args[i] === '-ngrok' && args[i + 1]) {
        ngrokToken = args[i + 1];
    }
    if (args[i] === '-tunnel' && args[i + 1]) {
        tunnelAddress = args[i + 1];
    }
}

const WORLD_TILES = config.worldTiles;
const FOOD_COUNT  = config.foodCount;
const COIN_COUNT  = config.coinCount;
const STATE_RATE  = config.stateRate;
const FOOD_RATE   = config.foodRate;
const COIN_RATE   = config.coinRate;

let nextId = 1;
let heartbeatInterval = null;
let ngrokListener = null;

const players = new Map();
const foods   = [];
const coins   = [];
const apples = []; 
const serverStartTime = Date.now();
const economyPath = path.join(__dirname, 'data', 'economy.json');
const SKIN_PRICES = {
    obsidian: 150, lava: 200, ice: 200, midnight: 120, toxic: 300,
    rich: 300, tex_snake: 350, tex_pixel: 400, tex_neon_grid: 400,
    tex_galaxy: 500, tex_fire: 500, tex_tomato: 1000
};
let economy = {};
try {
    fs.mkdirSync(path.dirname(economyPath), { recursive: true });
    if (fs.existsSync(economyPath)) economy = JSON.parse(fs.readFileSync(economyPath, 'utf8'));
} catch (e) {
    logEconomyError('Could not load economy data: ' + e.message);
    economy = {};
}
function logEconomyError(message) { console.error('[Economy] ' + message); }
function profileKey(name) { return String(name || 'Player').trim().toLowerCase().slice(0, 24) || 'player'; }
function getProfile(name) {
    const key = profileKey(name);
    if (!economy[key]) economy[key] = { coins: 0, skins: [], equipped: 'bright_blue' };
    const p = economy[key];
    if (!Number.isSafeInteger(p.coins) || p.coins < 0) p.coins = 0;
    if (!Array.isArray(p.skins)) p.skins = [];
    if (!p.equipped) p.equipped = 'bright_blue';
    return p;
}
function saveEconomy() {
    try {
        fs.mkdirSync(path.dirname(economyPath), { recursive: true });
        const temp = economyPath + '.tmp';
        fs.writeFileSync(temp, JSON.stringify(economy, null, 2));
        fs.renameSync(temp, economyPath);
    } catch (e) { logEconomyError('Could not save economy: ' + e.message); }
}
function sendProfile(player) {
    const profile = getProfile(player.name);
    player.coins = profile.coins;
    send(player.ws, { type: 'profile', coins: profile.coins, skins: profile.skins, equipped: profile.equipped });
}


function getUptime() {
    const diff = Date.now() - serverStartTime;
    const seconds = Math.floor((diff / 1000) % 60);
    const minutes = Math.floor((diff / 1000 / 60) % 60);
    const hours = Math.floor((diff / 1000 / 60 / 60) % 24);
    const days = Math.floor(diff / 1000 / 60 / 60 / 24);

    let parts = [];
    if (days > 0) parts.push(`${days} day(s)`);
    if (hours > 0) parts.push(`${hours}h`);
    if (minutes > 0) parts.push(`${minutes}m`);
    parts.push(`${seconds}s`);

    return `Server Est: ${parts.join(' ')}`;
}

let appleRespawnTimer = null;
function spawnApple() {
    if (apples.length) return;
    let spot = null;
    for (let attempt = 0; attempt < 500; attempt++) {
        const candidate = randomTile();
        const blocked = [...players.values()].some(p => p.body.some(part => part[0] === candidate.x && part[1] === candidate.y))
            || foods.some(f => f.x === candidate.x && f.y === candidate.y)
            || coins.some(c => c.x === candidate.x && c.y === candidate.y);
        if (!blocked) { spot = candidate; break; }
    }
    if (!spot) return;
    apples.push(spot);
    broadcastApples();
}
function scheduleAppleRespawn() {
    if (appleRespawnTimer) clearTimeout(appleRespawnTimer);
    appleRespawnTimer = setTimeout(spawnApple, 4 * 60 * 1000 + Math.floor(Math.random() * 8 * 60 * 1000));
}
function broadcastApples() {
    broadcastAll({ type: 'apple_spawn', apples: apples.map(a => [a.x, a.y]) });
}
// No fixed spawn points and no startup batch: the first apple also waits for a random interval.
scheduleAppleRespawn();

//send(ws, { type: 'apple_spawn', apples: apples.map(a => [a.x, a.y]) });

function log(msg) { const ts = new Date().toISOString().substring(11, 23); console.log(`[${ts}] ${msg}`); }
function randomTile() { 
    return { 
        x: Math.floor(Math.random() * WORLD_TILES), 
        y: Math.floor(Math.random() * WORLD_TILES) 
    }; 
}
function pickRarity(){
    const r = Math.random() * 100;
    if (r < 2) return 3;
    if (r < 10) return 2;
    if (r < 30) return 1;
    return 0;
}
function spawnFood()  { 
    const { x, y } = randomTile();
    foods.push({ x, y, rarity: pickRarity() }); 
}
function spawnCoin()  { const { x, y } = randomTile(); coins.push({ x, y }); }
function ensureFoods() { while (foods.length < FOOD_COUNT) spawnFood(); }
function ensureCoins() { while (coins.length < COIN_COUNT) spawnCoin(); }
function send(ws, obj) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj)); }
function broadcast(obj, excludeId = -1) {
    const msg = JSON.stringify(obj);
    players.forEach(p => { if (p.id !== excludeId && p.ws.readyState === WebSocket.OPEN) p.ws.send(msg); });
}
function broadcastAll(obj) { const msg = JSON.stringify(obj); players.forEach(p => { if (p.ws.readyState === WebSocket.OPEN) p.ws.send(msg); }); }

function buildStatePayload() {
    const plist = [];
    players.forEach(p => plist.push({ id: p.id, name: p.name, skin: p.skin, body: p.body, dir: p.dir, alive: p.alive, score: p.score, coins: p.coins }));
    return { type: 'state', uptime: getUptime(), worldTiles: WORLD_TILES, players: plist };
}

function checkPlayerCollisions(moverId, hx, hy) {
    for (const [id, p] of players) {
        if (!p.alive) continue;
        for (let i = 1; i < p.body.length; i++)
            if (p.body[i][0] === hx && p.body[i][1] === hy) return p;
    }
    return null;
}

function checkFoodEaten(hx, hy) { for (let i = 0; i < foods.length; i++) if (foods[i].x === hx && foods[i].y === hy) return i; return -1; }
function checkCoinEaten(hx, hy) { for (let i = 0; i < coins.length; i++) if (coins[i].x === hx && coins[i].y === hy) return i; return -1; }

function broadcastFoods() { broadcastAll({ type: 'foods', foods: foods.map(f => [f.x, f.y, f.rarity]) }); }
function broadcastCoins() { broadcastAll({ type: 'coin_spawn', coins: coins.map(c => [c.x, c.y]) }); }

function killPlayer(id, killer, reason) {
    const p = players.get(id); if (!p) return;
    p.alive = false;
    const killerName = killer ? killer.name : '';
    log(`${p.name}${killerName ? ' was killed by ' + killerName : ' died (' + reason + ')'}`);
    broadcastAll({ type: 'player_died', id, killer: killerName });
}

const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('Sneaky Snakes dedicated network server is Active.n0n0\n');
});

server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.error(`\x1b[31mThere's already a server running on port (${port}).\x1b[0m`);
        process.exit(1);
    } else {
        console.error(`Server Error:`, err.message);
    }
});

const wss = new WebSocket.Server({ server });

ensureFoods();
ensureCoins();

// ─── Função de registro na API Vercel ───────────────────────────────────────
const registerServer = async (host,targetPort) => {
    try{
        await axios.post(`${VERCEL_API_URL}/register`,{
            host:host,
            port:targetPort,
            secure:
                targetPort==443||
                targetPort==='443',
            name:`Official Server @ ${host}:${targetPort}`,
            timestamp:Date.now()
        });

        log(`\x1b[32m[API Vercel] Heartbeat enviado para ${host}:${targetPort}\x1b[0m`);
    }catch(error){
        log(`\x1b[31m[API Vercel] Erro ao registrar: ${error.message}\x1b[0m`);
    }
};

server.listen(port, async () => {
    log(`Starting network server on port ${port}...`);
    log(`World: ${WORLD_TILES}x${WORLD_TILES} | Food: ${FOOD_COUNT} | Coins: ${COIN_COUNT}`);

    let publicHost = 'localhost';
    let publicPort = port;

    if (ngrokToken) {
        try {

            const ngrok = require('@ngrok/ngrok');

            ngrokListener = await ngrok.connect({
                proto: 'http',
                addr: port,
                authtoken: ngrokToken,
            });
            const url = ngrokListener.url();
            const withoutProto = url.replace('https://', '').replace('http://', '');
            publicHost = withoutProto;
            publicPort = '443'; // HTTPS/WSS usa port 443

            log(`\x1b[32mTunnel active: \x1b[1m${url}\x1b[0m`);
            log(`\x1b[32mPlayers should join in: \x1b[1mwss://${withoutProto}\x1b[0m`);

        } catch (err) {
            log(`\x1b[31mError${err.message}\x1b[0m`);
            log(`\x1b[33mVerify if ngrok is installed: npm install @ngrok/ngrok\x1b[0m`);
        }

    } else if (tunnelAddress) {
        const parts = tunnelAddress.split(':');
        publicHost = parts[0];
        publicPort = parts[1];
        log(`\x1b[33mUsing manual adress: ${tunnelAddress}\x1b[0m`);

    } else {
        log(`\x1b[33mNo tunnel configured; Using local IP. If you have Radmin, you can share your Radmin IP and port with your friends!\x1b[0m`);
    }

    if (isOfficial) {
        await registerServer(publicHost, publicPort);
        heartbeatInterval = setInterval(() => registerServer(publicHost, publicPort), 30000);
    }
});
""
wss.on('connection', (ws, req) => {
const id = nextId++;
    const ip = req.socket.remoteAddress;
    const player = { id, ws, name: 'Player' + id, skin: 'bright_blue', body: [], dir: [1, 0], alive: false, score: 0, coins: 0 };
    players.set(id, player);

    send(ws, { type: 'welcome', id, worldTiles: WORLD_TILES });
    send(ws, { type: 'foods', foods: foods.map(f => [f.x, f.y, f.rarity]) });
    send(ws, { type: 'coin_spawn', coins: coins.map(c => [c.x, c.y]) });
    
    send(ws, { type: 'apple_spawn', apples: apples.map(a => [a.x, a.y]) });

    ws.on('message', raw => {
        let msg; try { msg = JSON.parse(raw); } catch { return; }
        switch (msg.type) {
            case 'join': {
                player.name = String(msg.name || ('Player' + id)).substring(0, 24);
                const requestedSkin = String(msg.skin || 'bright_blue').substring(0, 40);
                // Skins compradas no Android ficam salvas localmente; o servidor apenas aplica a aparência enviada.
                player.skin = (requestedSkin === 'bright_blue' || requestedSkin === 'bright_green' || Object.prototype.hasOwnProperty.call(SKIN_PRICES, requestedSkin)) ? requestedSkin : 'bright_blue';
                player.coins = 0;
                player.alive = true;
                const sx = Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 10 - 5);
                const sy = Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 10 - 5);
                player.body = []; for (let i = 0; i < 4; i++) player.body.push([sx - i, sy]);
                player.dir = [1, 0]; player.score = 0;
                sendProfile(player);
                log(`\x1b[34m[[game logs]] Player ${player.name} joined the Game!\x1b[0m`);
                broadcast({ type: 'player_joined', id, name: player.name, skin: player.skin }, id);
                break;
            }
            case 'move': {
                if (!player.alive) break;
                const [dx, dy] = Array.isArray(msg.dir) ? msg.dir : [1, 0];
                const [hx, hy] = Array.isArray(msg.head) ? msg.head : [0, 0];
                player.dir = [dx, dy];
                if (Array.isArray(msg.body) && msg.body.length > 0) {
                    player.body = msg.body.slice(0, 512);
                } else {
                    if (player.body.length > 0) {
                        player.body.unshift([hx, hy]);
                        player.body.pop();
                    }
                }
                if (hx < 0 || hx >= WORLD_TILES || hy < 0 || hy >= WORLD_TILES) {
                    killPlayer(id, null, 'border'); break;
                }
                const fi = checkFoodEaten(hx, hy);
                if (fi >= 0) {
                    const food = foods[fi]; foods.splice(fi, 1);
                    player.score += ([10, 30, 80, 200][food.rarity] || 10);
                    ensureFoods(); broadcastFoods();
                }
                break;
            }
            case 'apple_collect': {
                const ax = msg.x, ay = msg.y;
                if (!player.alive || !player.body.length || Math.abs(player.body[0][0] - ax) + Math.abs(player.body[0][1] - ay) !== 1) break;
                const ai = apples.findIndex(a => a.x === ax && a.y === ay);
                if (ai >= 0) {
                    apples.splice(ai, 1);
                    player.score += 50;
                    broadcastApples();
                    scheduleAppleRespawn();
                }
                break;
            }
            case 'coin_collect': {
                const cx = msg.x, cy = msg.y;
                if (!player.alive || !player.body.length || Math.abs(player.body[0][0] - cx) + Math.abs(player.body[0][1] - cy) !== 1) break;
                const ci = checkCoinEaten(cx, cy);
                if (ci >= 0) {
                    coins.splice(ci, 1);
                    const profile = getProfile(player.name);
                    profile.coins += 3;
                    player.coins = profile.coins;
                    saveEconomy();
                    ensureCoins();
                    broadcastCoins();
                    sendProfile(player);
                }
                break;
            }
            case 'identify': {
                player.name = String(msg.name || ('Player' + id)).substring(0, 24);
                sendProfile(player);
                break;
            }
            case 'profile': {
                sendProfile(player);
                break;
            }
            case 'shop_buy': {
                const skinId = String(msg.skin || '');
                const cost = SKIN_PRICES[skinId];
                const profile = getProfile(player.name);
                if (!cost) { send(ws, { type: 'shop_result', success: false, message: 'Skin inválida.' }); break; }
                if (profile.skins.includes(skinId)) { send(ws, { type: 'shop_result', success: false, message: 'Você já possui essa skin.' }); break; }
                if (profile.coins < cost) { send(ws, { type: 'shop_result', success: false, message: 'Moedas insuficientes.' }); break; }
                profile.coins -= cost;
                profile.skins.push(skinId);
                profile.equipped = skinId;
                player.coins = profile.coins;
                player.skin = skinId;
                saveEconomy();
                send(ws, { type: 'shop_result', success: true, message: 'Skin comprada!' });
                sendProfile(player);
                broadcastAll(buildStatePayload());
                break;
            }
            case 'skin_equip': {
                const skinId = String(msg.skin || '');
                const profile = getProfile(player.name);
                if (skinId === 'bright_blue' || skinId === 'bright_green' || profile.skins.includes(skinId)) {
                    profile.equipped = skinId;
                    player.skin = skinId;
                    saveEconomy();
                    send(ws, { type: 'shop_result', success: true, message: 'Skin equipada!' });
                    sendProfile(player);
                    broadcastAll(buildStatePayload());
                } else {
                    send(ws, { type: 'shop_result', success: false, message: 'Você ainda não possui essa skin.' });
                }
                break;
            }
            case 'die': {
                if (!player.alive) break;
                log(`${player.name} died (${msg.reason})`);
                player.alive = false;
                broadcastAll({ type: 'player_died', id, killer: '' });
                break;
            }
            case 'respawn': {
                // O cliente pode ter marcado a morte localmente antes de receber a confirmação do servidor.
                // Por isso, o pedido de respawn é tratado como reinício seguro mesmo se alive ainda estiver true.
                const rx = Math.max(4, Math.min(WORLD_TILES - 5, Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 9) - 4));
                const ry = Math.max(4, Math.min(WORLD_TILES - 5, Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 9) - 4));
                player.alive = true; player.body = [];
                for (let i = 0; i < 4; i++) player.body.push([rx - i, ry]);
                player.dir = [1, 0]; player.score = 0;
                send(ws, { type: 'respawned', x: rx, y: ry });
                broadcastAll(buildStatePayload());
                break;
            }
        }
    });

    ws.on('close', () => {
        log(`Player ${player.name} left`);
        players.delete(id);
        broadcastAll({ type: 'player_left', id });
    });

    ws.on('error', err => log(`! Error on #${id}: ${err.message}`));
});

/*setInterval(() => {  broadcastAll(buildStatePayload()); }, STATE_RATE);
setInterval(() => { ensureFoods(); broadcastFoods(); }, FOOD_RATE);
setInterval(() => { ensureCoins(); broadcastCoins(); }, COIN_RATE);*/
setInterval(() => { broadcastAll(buildStatePayload()); }, STATE_RATE);
setInterval(() => { ensureFoods(); broadcastFoods(); }, FOOD_RATE);
setInterval(() => { ensureCoins(); broadcastCoins(); }, COIN_RATE);
setInterval(() => { if (players.size === 0) return; const names = [...players.values()].map(p => `${p.name}(${p.body.length})`).join(', '); log(`Players: ${players.size} | ${names}`); }, 10000);

const cleanup = async () => {
    if (heartbeatInterval) clearInterval(heartbeatInterval);

    if (ngrokListener) {
        try {
            await ngrokListener.close();
        } catch (err) {
            log(`Error ocurred while ending tunnel: ${err.message}`);
        }
    }

    if (isOfficial) {
        try {
            await axios.post(
                `${VERCEL_API_URL}/unregister`,
                {
                    host: publicHost,
                    port: publicPort
                }
            );
            log('Server removed');
        } catch (error) {
            log(`Error ocurred while registering: ${error.message}`);
        }
    }

    wss.close(() => {
        server.close(() => {
            process.exit(0);
        });
    });
};

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);