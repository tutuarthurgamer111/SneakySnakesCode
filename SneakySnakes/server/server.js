const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const readline = require('readline');

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
const DATA_DIR = path.join(__dirname, 'data');
const ACCOUNTS_FILE = path.join(DATA_DIR, 'players.json');
let accounts = {};
try { if (fs.existsSync(ACCOUNTS_FILE)) accounts = JSON.parse(fs.readFileSync(ACCOUNTS_FILE, 'utf8')) || {}; }
catch (err) { console.error('[Accounts] Failed to load accounts:', err.message); accounts = {}; }
function saveAccounts() {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    const temp = ACCOUNTS_FILE + '.tmp';
    fs.writeFileSync(temp, JSON.stringify(accounts, null, 2), 'utf8');
    fs.renameSync(temp, ACCOUNTS_FILE);
}
function validUuid(value) {
    return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
function sendProfile(player) {
    if (!player.account) return;
    send(player.ws, { type: 'profile', uuid: player.account.uuid, name: player.account.name,
        coins: player.account.coins,
        unlockedSkins: player.account.unlockedSkins || '',
        currentSkinId: player.account.currentSkinId || 'bright_green' });
}
const foods   = [];
const coins   = [];
const apples = []; 
const serverStartTime = Date.now();

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

function randomFreeTile() {
    for (let i = 0; i < 500; i++) {
        const p = randomTile();
        const used = foods.some(v => v.x === p.x && v.y === p.y) ||
            coins.some(v => v.x === p.x && v.y === p.y) ||
            apples.some(v => v.x === p.x && v.y === p.y) ||
            [...players.values()].some(v => v.alive && v.body.some(seg => seg[0] === p.x && seg[1] === p.y));
        if (!used) return p;
    }
    return randomTile();
}
function spawnApple() {
    const { x, y } = randomFreeTile();
    apples.push({ x, y });
}

function ensureApples() {
    while (apples.length < 5) spawnApple();
}

function broadcastApples() {
    broadcastAll({ type: 'apple_spawn', apples: apples.map(a => [a.x, a.y]) });
}

ensureApples();
setInterval(() => { ensureApples(); broadcastApples(); }, 5000);

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
    players.forEach(p => plist.push({ id: p.id, name: p.name, skin: p.skin, body: p.body, dir: p.dir, alive: p.alive }));
    return { type: 'state', uptime: getUptime(), players: plist };
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

    send(ws, { type: 'welcome', id });
    send(ws, { type: 'foods', foods: foods.map(f => [f.x, f.y, f.rarity]) });
    send(ws, { type: 'coin_spawn', coins: coins.map(c => [c.x, c.y]) });
    
    send(ws, { type: 'apple_spawn', apples: apples.map(a => [a.x, a.y]) });

    ws.on('message', raw => {
        let msg; try { msg = JSON.parse(raw); } catch { return; }
        switch (msg.type) {
            case 'join': {
                player.name = String(msg.name || ('Player' + id)).substring(0, 24);
                player.skin = String(msg.skin || 'bright_blue').substring(0, 40);

                const uuid = validUuid(msg.playerId) ? msg.playerId.toLowerCase() : null;
                if (!uuid) {
                    send(ws, { type: 'error', message: 'Invalid player UUID. Please update the game.' });
                    break;
                }
                let account = accounts[uuid];
                if (!account) {
                    account = {
                        uuid, name: player.name,
                        coins: Math.max(0, Math.min(100000000, Number(msg.legacyCoins) || 0)),
                        unlockedSkins: String(msg.unlockedSkins || '').substring(0, 2000),
                        currentSkinId: String(msg.currentSkinId || 'bright_green').substring(0, 64),
                        lastIp: ip, createdAt: new Date().toISOString()
                    };
                    accounts[uuid] = account;
                } else {
                    account.name = player.name;
                    account.lastIp = ip;
                    const merged = new Set([
                        ...String(account.unlockedSkins || '').split('|'),
                        ...String(msg.unlockedSkins || '').split('|')
                    ].map(v => v.trim()).filter(Boolean));
                    account.unlockedSkins = [...merged].join('|');
                    const equipped = String(msg.currentSkinId || '');
                    if (equipped && merged.has(equipped)) account.currentSkinId = equipped;
                }
                account.lastSeen = new Date().toISOString();
                player.uuid = uuid;
                player.account = account;
                saveAccounts();
                sendProfile(player);

                player.alive = true;
                const sx = Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 10 - 5);
                const sy = Math.floor(WORLD_TILES / 2) + Math.floor(Math.random() * 10 - 5);
                player.body = []; for (let i = 0; i < 4; i++) player.body.push([sx - i, sy]);
                player.dir = [1, 0]; player.score = 0; player.coins = 0;
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
            case 'coin_collect': {
                const cx = msg.x, cy = msg.y;
                const ci = checkCoinEaten(cx, cy);
                if (ci >= 0) {
                    coins.splice(ci, 1);
                    player.coins++;
                    if (player.account) {
                        player.account.coins = Math.max(0, Number(player.account.coins) || 0) + 1;
                        player.account.lastSeen = new Date().toISOString();
                        saveAccounts(); // Save each coin immediately.
                        sendProfile(player);
                    }
                    ensureCoins();
                    broadcastCoins();
                }
                break;
            }
            case 'apple_collect': {
                const ax = Number(msg.x), ay = Number(msg.y);
                const ai = apples.findIndex(a => a.x === ax && a.y === ay);
                if (ai >= 0) {
                    apples.splice(ai, 1);
                    ensureApples();
                    broadcastApples();
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
                const [rx, ry] = Array.isArray(msg.head) ? msg.head : [32, 32];
                player.alive = true; player.body = [];
                for (let i = 0; i < 4; i++) player.body.push([rx - i, ry]);
                player.dir = [1, 0]; player.score = 0;
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

// Admin console command: addcoins/givecoins "Player Name" 500
if (process.stdin && !process.stdin.destroyed) {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.on('line', line => {
        const input = line.trim();
        const m = input.match(/^(?:addcoins|givecoins)\s+"([^"]+)"\s+(\d+)$/i) ||
                  input.match(/^(?:addcoins|givecoins)\s+(\S+)\s+(\d+)$/i);
        if (!m) { if (input) console.log('Use: addcoins "Player Name" 500'); return; }
        const amount = Number(m[2]);
        if (!Number.isSafeInteger(amount) || amount < 1 || amount > 100000000) {
            console.log('Amount must be between 1 and 100000000.'); return;
        }
        const name = m[1].trim().toLowerCase();
        const found = Object.values(accounts).filter(a => String(a.name || '').toLowerCase() === name);
        if (found.length !== 1) {
            console.log(found.length ? 'Name is ambiguous; use a unique player name.' : 'Player not found; they must join once first.');
            return;
        }
        const account = found[0];
        account.coins = Math.max(0, Number(account.coins) || 0) + amount;
        account.lastSeen = new Date().toISOString();
        saveAccounts();
        for (const p of players.values()) if (p.uuid === account.uuid) sendProfile(p);
        console.log('[Coins] +' + amount + ' for ' + account.name + '. Balance: ' + account.coins);
    });
}

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