#!/usr/bin/env node
'use strict';

// Uso: node givescore.js "NomeDaConta" quantidade
// Adiciona pontos ao recorde salvo (highScore) da conta, sem alterar moedas.

const fs = require('fs');
const path = require('path');

const [rawUsername, rawAmount, ...extra] = process.argv.slice(2);
if (!rawUsername || !rawAmount || extra.length || !/^\d+$/.test(rawAmount)) {
    console.error('Uso: node givescore.js "NomeDaConta" quantidade');
    process.exit(1);
}

const username = rawUsername.trim().normalize('NFKC').toLowerCase();
const amount = Number(rawAmount);

if (!username || !Number.isSafeInteger(amount) || amount <= 0) {
    console.error('Informe uma conta válida e uma quantidade positiva de pontos.');
    process.exit(1);
}

const accountsPath = path.join(__dirname, 'data', 'accounts.json');
if (!fs.existsSync(accountsPath)) {
    console.error('Arquivo data/accounts.json não encontrado. Execute o script na pasta do servidor.');
    process.exit(1);
}

let accounts;
try {
    accounts = JSON.parse(fs.readFileSync(accountsPath, 'utf8'));
} catch (error) {
    console.error('Não foi possível ler accounts.json:', error.message);
    process.exit(1);
}

const accountKey = Object.keys(accounts).find(key =>
    String(key).trim().normalize('NFKC').toLowerCase() === username ||
    String(accounts[key]?.username || '').trim().normalize('NFKC').toLowerCase() === username
);

if (!accountKey || !accounts[accountKey]) {
    console.error('Conta não encontrada:', rawUsername);
    process.exit(1);
}

const current = Number.isSafeInteger(accounts[accountKey].highScore) && accounts[accountKey].highScore >= 0
    ? accounts[accountKey].highScore : 0;
if (current + amount > Number.MAX_SAFE_INTEGER) {
    console.error('O resultado ultrapassa o limite seguro de pontos.');
    process.exit(1);
}

const next = current + amount;
const backupPath = accountsPath + '.backup';
const tempPath = accountsPath + '.tmp';

try {
    if (!fs.existsSync(backupPath)) fs.copyFileSync(accountsPath, backupPath);
    accounts[accountKey].highScore = next;
    fs.writeFileSync(tempPath, JSON.stringify(accounts, null, 2) + '\n', { mode: 0o600 });
    fs.renameSync(tempPath, accountsPath);
    console.log('Pontos adicionados com sucesso!');
    console.log('Conta:', accounts[accountKey].username || rawUsername);
    console.log('Recorde anterior:', current);
    console.log('Adicionado:', amount);
    console.log('Novo recorde:', next);
} catch (error) {
    try { if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath); } catch (_) {}
    console.error('Falha ao salvar o recorde:', error.message);
    process.exit(1);
}
