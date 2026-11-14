const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// A small DOM harness tests client state changes; it does not replace visual QA.
function client() {
  const html = readFileSync(path.join(__dirname, '../web/index.html'), 'utf8');
  const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
  class Element {
    constructor() {
      this.classes = new Set(); this.value = ''; this.textContent = ''; this.innerHTML = '';
      this.children = []; this.listeners = {}; this.disabled = false;
      this.classList = {
        toggle:(name, force) => { if (force) this.classes.add(name); else this.classes.delete(name); },
        add:name => this.classes.add(name), remove:name => this.classes.delete(name), contains:name => this.classes.has(name)
      };
    }
    addEventListener(type, listener) { this.listeners[type] = listener; }
    replaceChildren(...children) { this.children = children; this.innerHTML = ''; }
    append(child) { this.children.push(child); }
    focus() { this.focused = true; }
    removeAttribute(name) { delete this[name]; }
    showModal() { this.open = true; }
    close() { this.open = false; this.listeners.close?.(); }
    getContext() { return {fillRect() {}, beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}}; }
    toDataURL() { return png; }
  }
  const elements = Object.fromEntries([...html.matchAll(/id="([^"]+)"/g)].map(match => [match[1], new Element()]));
  for (const match of html.matchAll(/id="([^"]+)"[^>]*class="[^"]*\bhidden\b/g)) elements[match[1]].classes.add('hidden');
  const pills = ['write', 'draw', 'guess'].map(phase => Object.assign(new Element(), {dataset:{phasePill:phase}}));
  class Socket {
    static OPEN = 1;
    constructor() { this.readyState = 1; this.sent = []; }
    send(data) { this.sent.push(JSON.parse(data)); }
  }
  const document = {
    getElementById:id => { assert(elements[id], `Missing HTML element: ${id}`); return elements[id]; },
    querySelector:() => new Element(),
    querySelectorAll:selector => selector === '[data-phase-pill]' ? pills : [],
    createElement:() => new Element()
  };
  const context = vm.createContext({document, WebSocket:Socket, location:{host:'localhost:8080', protocol:'http:', origin:'http://localhost:8080'}, console, setTimeout});
  vm.runInContext(readFileSync(path.join(__dirname, '../web/app.js'), 'utf8'), context);
  const socket = vm.runInContext('socket', context);
  const message = data => socket.onmessage({data:JSON.stringify(data)});
  socket.onopen();
  message({type:'joined', playerId:'me', room:'TEST'});
  message({type:'roster', players:[{id:'me', name:'Same'}, {id:'other', name:'Same'}], hostId:'me', state:'playing'});
  const phase = (round, kind, assignment = null) => message({type:'phase', gameId:'game', round, totalRounds:5, phase:kind, assignment});
  return {elements, socket, message, phase, png};
}

test('client repeats drawing/guessing with fresh private assignments and correct round labels', () => {
  const {elements:e, phase, png} = client();
  phase(1, 'write');
  assert.equal(e['phase-label'].textContent, 'ROUND 1 OF 5');
  assert(!e['write-panel'].classList.contains('hidden'));
  phase(2, 'draw', {text:'Original prompt', skipped:false});
  assert.equal(e['given-prompt'].textContent, 'Original prompt');
  phase(3, 'guess', {text:png, skipped:false});
  assert.equal(e['given-prompt'].textContent, '');
  assert.equal(e['given-drawing'].children[0].src, png);
  e['guess-input'].value = 'Old guess';
  phase(4, 'draw', {text:'New guess becomes the drawing prompt', skipped:false});
  assert.equal(e['given-prompt'].textContent, 'New guess becomes the drawing prompt');
  assert.equal(e['given-drawing'].children.length, 0);
  assert.equal(e['guess-input'].value, '');
  assert.equal(e['phase-label'].textContent, 'ROUND 4 OF 5');
  phase(5, 'guess', {text:png, skipped:false});
  assert.equal(e['round-progress'].value, 5);
  assert(!e['guess-panel'].classList.contains('hidden'));
  assert(e['draw-panel'].classList.contains('hidden'));
});

test('client waits for acknowledgement, handles rejection, and ignores stale progress', () => {
  const {elements:e, socket, message, phase} = client();
  phase(1, 'write');
  e['prompt-input'].value = 'A cat'; e['submit-prompt'].onclick();
  assert.equal(socket.sent.at(-1).round, '1');
  assert.equal(socket.sent.at(-1).gameId, 'game');
  assert.equal(socket.sent.at(-1).text, 'A cat');
  assert.equal(e['submit-prompt'].disabled, true);
  assert(e.waiting.classList.contains('hidden'));
  const sent = socket.sent.length; e['submit-prompt'].onclick(); assert.equal(socket.sent.length, sent);
  message({type:'error', text:'Try again'});
  assert.equal(e['submit-prompt'].disabled, false);
  assert.equal(e['prompt-input'].value, 'A cat');
  e['submit-prompt'].onclick();
  message({type:'accepted', gameId:'game', round:1});
  assert(!e.waiting.classList.contains('hidden'));
  assert(e['write-panel'].classList.contains('hidden'));
  message({type:'progress', gameId:'game', round:1, ready:1, total:2});
  assert.equal(e['waiting-text'].textContent, 'Waiting for 1 player…');
  phase(2, 'draw', {text:'Next prompt'});
  message({type:'progress', gameId:'game', round:1, ready:2, total:2});
  message({type:'accepted', gameId:'old-game', round:2});
  assert.equal(e['submission-count'].textContent, '0 / 2 ready');
  assert(e.waiting.classList.contains('hidden'));
});

test('reveal renders complete chains with safe text, attribution, drawings and disconnect gaps', () => {
  const {elements:e, message, png} = client();
  const entry = (round, kind, text, skipped = false) => ({round, kind, text, skipped, playerId:round === 1 ? 'me' : 'other', player:'<Player>'});
  message({type:'reveal', chains:[{id:0, owner:'<Owner>', entries:[
    entry(1, 'write', '<script>Original</script>'), entry(2, 'draw', png),
    entry(3, 'guess', 'A different idea'), entry(4, 'draw', png), entry(5, 'guess', '', true)
  ]}]});
  assert(!e.results.classList.contains('hidden'));
  const result = e['result-list'].innerHTML;
  assert.equal((result.match(/<li class="chat-entry/g) || []).length, 5);
  assert.equal((result.match(/class="drawing-preview"/g) || []).length, 2);
  assert(result.includes('&lt;script&gt;Original&lt;/script&gt;') && !result.includes('<script>'));
  assert(result.includes('&lt;Player&gt; (you)'));
  assert(result.indexOf('Round 2') < result.indexOf('Round 3') && result.indexOf('Round 3') < result.indexOf('Round 4'));
  assert(result.includes('No submission — player disconnected.'));
  message({type:'lobby'});
  assert.equal(e['result-list'].innerHTML, '');
  assert(!e.lobby.classList.contains('hidden'));
});

test('missing assignments explain gaps and replay is restricted to the host', () => {
  const {elements:e, message, phase} = client();
  phase(2, 'draw', {skipped:true, text:''});
  assert(e['given-prompt'].textContent.includes('disconnected'));
  phase(3, 'guess', {skipped:true, text:''});
  assert(e['given-drawing'].children[0].textContent.includes('disconnected'));
  message({type:'roster', players:[{id:'me', name:'Same'}, {id:'other', name:'Same'}], hostId:'other', state:'reveal'});
  assert.equal(e['back-to-lobby'].disabled, true);
});
