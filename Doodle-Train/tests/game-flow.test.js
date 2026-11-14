// Run with Node 22+ and a JDK on PATH: node --test tests/game-flow.test.js
const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {spawn, execFileSync} = require('node:child_process');
const {mkdtempSync, rmSync} = require('node:fs');
const {tmpdir} = require('node:os');
const path = require('node:path');
const net = require('node:net');
const {randomBytes} = require('node:crypto');

const root = path.resolve(__dirname, '..');
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==', 'base64');
let server, build, port;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

before(async () => {
  build = mkdtempSync(path.join(tmpdir(), 'doodle-relay-test-'));
  execFileSync('javac', [
    '-encoding', 'UTF-8', '--release', '17', '-d', build,
    '../backend/src/main/java/com/example/draw/game/GameServer.java'
  ], {cwd:root});
  const reservation = net.createServer();
  await new Promise(resolve => reservation.listen(0, '127.0.0.1', resolve));
  port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  server = spawn('java', ['-cp', build, 'com.example.draw.game.GameServer', String(port)], {cwd:root, windowsHide:true});
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Server startup timed out')), 5000);
    server.stdout.once('data', () => { clearTimeout(timeout); resolve(); });
    server.once('error', error => { clearTimeout(timeout); reject(error); });
  });
});
after(async () => {
  if (server && server.exitCode === null) {
    const exited = new Promise(resolve => server.once('exit', resolve));
    server.kill(); await exited;
  }
  if (build) rmSync(build, {recursive:true, force:true});
});

class Peer {
  constructor() {
    this.socket = new WebSocket(`ws://127.0.0.1:${port}/ws`);
    this.messages = [];
    this.waiters = [];
    this.socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      this.messages.push(message);
      for (const waiter of [...this.waiters]) {
        if (waiter.predicate(message)) { waiter.resolve(message); this.waiters.splice(this.waiters.indexOf(waiter), 1); }
      }
    });
  }
  async open() {
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, {once:true});
      this.socket.addEventListener('error', reject, {once:true});
    });
    return this;
  }
  send(message) { this.socket.send(JSON.stringify(message)); }
  wait(predicate, since = 0) {
    const existing = this.messages.slice(since).find(predicate);
    if (existing) return Promise.resolve(existing);
    return new Promise((resolve, reject) => {
      const waiter = {predicate, resolve:message => { clearTimeout(timeout); resolve(message); }};
      const timeout = setTimeout(() => {
        this.waiters.splice(this.waiters.indexOf(waiter), 1);
        reject(new Error(`Message timeout; received: ${this.messages.map(m => m.type + ':' + (m.round || '')).join(', ')}`));
      }, 5000);
      this.waiters.push(waiter);
    });
  }
  phase(round, since = 0) { return this.wait(m => m.type === 'phase' && m.round === round, since); }
  close() {
    if (this.socket.readyState === WebSocket.CLOSED) return Promise.resolve();
    return new Promise(resolve => { this.socket.addEventListener('close', resolve, {once:true}); this.socket.close(); });
  }
}
async function roomOf(count, names = []) {
  const peers = [];
  for (let i = 0; i < count; i++) {
    const peer = await new Peer().open(); peers.push(peer);
    if (i === 0) peer.send({type:'create', name:names[i] || `Player ${i}`});
    else peer.send({type:'join', room:peers[0].room, name:names[i] || `Player ${i}`});
    peer.id = (await peer.wait(m => m.type === 'joined')).playerId;
    if (i === 0) {
      peers[0].room = peers[0].messages.find(m => m.type === 'joined').room;
      assert.match(peers[0].room, /^\d{6}$/);
    }
  }
  await peers[0].wait(m => m.type === 'roster' && m.players.length === count);
  return peers;
}
async function accepted(peer, phase, text) {
  const since = peer.messages.length;
  peer.send({type:'submission', gameId:phase.gameId, round:String(phase.round), phase:phase.phase, text});
  await peer.wait(m => m.type === 'accepted' && m.round === phase.round && m.gameId === phase.gameId, since);
}
function payload(step, player, big = false) {
  return step % 2 === 1
    ? 'data:image/png;base64,' + Buffer.concat([png, big ? randomBytes(80_000) : Buffer.from(`${step}:${player}`)]).toString('base64')
    : `${step === 0 ? 'Prompt' : 'Guess'} ${step}:${player} — "cat" \\n\n\t🤖 සිංහල`;
}

for (const count of [2, 3, 4, 5, 6, 8]) {
  test(`${count} players: synchronized complete chains, private assignments, replay`, async t => {
    const peers = await roomOf(count, count === 4 ? ['Same', 'Same', 'Third', 'Fourth'] : []);
    t.after(() => Promise.all(peers.map(p => p.close())));
    const expected = Array.from({length:count}, () => []);
    const visited = peers.map(() => new Set());
    peers[0].send({type:'start'});
    for (let step = 0; step < count; step++) {
      const phases = await Promise.all(peers.map(p => p.phase(step + 1)));
      for (let i = 0; i < count; i++) {
        const phase = phases[i], chain = (i - step + count) % count;
        assert.equal(phase.totalRounds, count);
        assert.equal(phase.phase, step === 0 ? 'write' : step % 2 === 1 ? 'draw' : 'guess');
        assert(!visited[i].has(chain)); visited[i].add(chain);
        assert(!('chains' in phase) && !('history' in phase));
        if (step === 0) assert.equal(phase.assignment, null);
        else {
          assert.equal(phase.assignment.text, expected[chain][step - 1].text);
          assert.equal(phase.assignment.playerId, expected[chain][step - 1].playerId);
          assert.notEqual(phase.assignment.playerId, peers[i].id);
          assert(!('entries' in phase.assignment));
        }
        expected[chain].push({text:payload(step, i, count === 5 && step === 1), playerId:peers[i].id});
      }
      // Hold the final player back to prove the room cannot advance early.
      for (let i = count - 2; i >= 0; i--)
        await accepted(peers[i], phases[i], expected[(i - step + count) % count][step].text);
      await peers[0].wait(m => m.type === 'progress' && m.round === step + 1 && m.ready === count - 1);
      await delay(25);
      assert(!peers[0].messages.some(m => (m.type === 'phase' && m.round > step + 1) || m.type === 'reveal'));
      await accepted(peers[count - 1], phases[count - 1], expected[(count - 1 - step + count) % count][step].text);
    }
    const reveals = await Promise.all(peers.map(p => p.wait(m => m.type === 'reveal')));
    for (const reveal of reveals) assert.deepEqual(reveal, reveals[0]);
    assert.equal(reveals[0].chains.length, count);
    reveals[0].chains.forEach((chain, c) => {
      assert.equal(chain.entries.length, count);
      assert.equal(new Set(chain.entries.map(e => e.playerId)).size, count);
      chain.entries.forEach((entry, step) => {
        assert.equal(entry.text, expected[c][step].text);
        assert.equal(entry.playerId, expected[c][step].playerId);
        assert.equal(entry.round, step + 1); assert.equal(entry.skipped, false);
      });
    });
    // A second game must start clean, with a new token, after the shared lobby.
    const since = peers[0].messages.length;
    peers[0].send({type:'lobby'});
    await Promise.all(peers.map(p => p.wait(m => m.type === 'lobby')));
    peers[0].send({type:'start'});
    const next = await peers[0].phase(1, since);
    assert.notEqual(next.gameId, reveals[0].gameId); assert.equal(next.assignment, null);
  });
}

test('server validates host, round, payload, duplicates, and rejects late joins', async t => {
  const peers = await roomOf(3);
  const roomCode = peers[0].room;
  t.after(() => Promise.all(peers.map(p => p.close())));
  const [host, guest] = peers;
  guest.send({type:'start'});
  await guest.wait(m => m.type === 'error' && m.text.includes('host'));
  host.send({type:'start'});
  const phase = await host.phase(1);
  const intruder = await new Peer().open(); t.after(() => intruder.close());
  intruder.send({type:'join', name:'Late', room:roomCode});
  await intruder.wait(m => m.type === 'error' && m.text.includes('playing'));
  assert(!intruder.messages.some(m => m.type === 'joined' || m.type === 'phase'));
  for (const change of [{gameId:'old'}, {round:'9'}, {phase:'guess'}, {text:''}, {text:'x'.repeat(121)}]) {
    const since = host.messages.length;
    host.send({type:'submission', gameId:phase.gameId, round:'1', phase:'write', text:'Valid', ...change});
    await host.wait(m => m.type === 'error', since);
  }
  for (const type of ['phase', 'reveal', 'lobby']) {
    const since = guest.messages.length; guest.send({type});
    await guest.wait(m => m.type === 'error', since);
  }
  host.socket.send('{"type":"submission","nested":{"type":"reveal"}}');
  await host.wait(m => m.type === 'error' && m.text.includes('Invalid'));
  await accepted(host, phase, 'Original');
  host.send({type:'submission', gameId:phase.gameId, round:'1', phase:'write', text:'Overwrite'});
  await accepted(peers[1], await peers[1].phase(1), 'Second');
  await accepted(peers[2], await peers[2].phase(1), 'Third');
  const drawPhases = await Promise.all(peers.map(p => p.phase(2)));
  assert.equal(drawPhases[1].assignment.text, 'Original');
  const since = host.messages.length;
  host.send({type:'submission', gameId:phase.gameId, round:'2', phase:'draw', text:'javascript:alert(1)'});
  await host.wait(m => m.type === 'error' && m.text.includes('PNG'), since);
  for (let i = 0; i < peers.length; i++) await accepted(peers[i], drawPhases[i], payload(1, i));
  const guesses = await Promise.all(peers.map(p => p.phase(3)));
  for (let i = 0; i < peers.length; i++) await accepted(peers[i], guesses[i], `Final ${i}`);
  const reveal = await host.wait(m => m.type === 'reveal');
  // A player joining at reveal sees the exact completed game and can join the next one.
  intruder.send({type:'join', name:'Late', room:roomCode});
  assert.deepEqual(await intruder.wait(m => m.type === 'reveal'), reveal);
});

test('disconnects preserve accepted work, skip missing turns, and transfer host', async t => {
  const peers = await roomOf(5);
  t.after(() => Promise.all(peers.map(p => p.close())));
  peers[0].send({type:'start'});
  await Promise.all(peers.map(p => p.phase(1)));
  await accepted(peers[0], await peers[0].phase(1), 'Preserved host prompt');
  await peers[0].close(); // Accepted contribution survives departure.
  await peers[1].close(); // Missing prompt and subsequent turns are explicit gaps.
  await peers[2].wait(m => m.type === 'roster' && m.players.length === 3 && m.hostId === peers[2].id);
  for (let round = 1; round <= 5; round++) {
    const phases = await Promise.all(peers.slice(2).map(p => p.phase(round)));
    for (let i = 2; i < 5; i++) await accepted(peers[i], phases[i - 2], payload(round - 1, i));
  }
  const reveal = await peers[2].wait(m => m.type === 'reveal');
  assert.equal(reveal.chains[0].entries[0].text, 'Preserved host prompt');
  assert.equal(reveal.chains[0].entries[0].skipped, false);
  for (const chain of reveal.chains) {
    assert.equal(chain.entries.length, 5);
    assert.equal(new Set(chain.entries.map(e => e.playerId)).size, 5);
    for (const entry of chain.entries) {
      const departed = entry.playerId === peers[0].id || entry.playerId === peers[1].id;
      assert.equal(entry.skipped, departed && !(entry.playerId === peers[0].id && entry.round === 1));
    }
  }
  peers[2].send({type:'lobby'});
  await peers[2].wait(m => m.type === 'lobby');
  peers[2].send({type:'start'});
  const next = await peers[2].phase(1, peers[2].messages.findIndex(m => m.type === 'lobby'));
  assert.equal(next.totalRounds, 3);
});

test('rooms are isolated and HTTP serves the updated client', async t => {
  const left = await roomOf(2), right = await roomOf(2);
  t.after(() => Promise.all([...left, ...right].map(p => p.close())));
  left[0].send({type:'start'}); await left[0].phase(1);
  await delay(25); assert(!right[0].messages.some(m => m.type === 'phase'));
  const html = await fetch(`http://127.0.0.1:${port}/`).then(r => r.text());
  assert(html.includes('drawing-dialog') && html.includes('round-progress')
      && html.includes('Six-digit room code') && html.includes('Create a room'));
  const js = await fetch(`http://127.0.0.1:${port}/app.js`).then(r => r.text());
  assert(js.includes('function beginPhase') && js.includes("type:'create'") && !js.includes('fromPrevious'));
});

test('game room codes are unique and unknown codes cannot create rooms', async t => {
  const host = await new Peer().open();
  const secondHost = await new Peer().open();
  const unknown = await new Peer().open();
  t.after(() => Promise.all([host, secondHost, unknown].map(peer => peer.close())));
  host.send({type:'create', name:'Host'});
  const firstRoom = await host.wait(message => message.type === 'joined');
  secondHost.send({type:'create', name:'Second host'});
  const secondRoom = await secondHost.wait(message => message.type === 'joined');
  assert.match(firstRoom.room, /^\d{6}$/);
  assert.match(secondRoom.room, /^\d{6}$/);
  assert.notEqual(firstRoom.room, secondRoom.room);
  unknown.send({type:'join', name:'Guest', room:'000000'});
  const error = await unknown.wait(message => message.type === 'error');
  assert.match(error.text, /not found/);
});
