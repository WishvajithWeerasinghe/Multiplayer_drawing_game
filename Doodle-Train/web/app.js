const $ = id => document.getElementById(id);
const screens = ['home', 'lobby', 'game', 'results'];
const show = screen => screens.forEach(id => $(id).classList.toggle('hidden', id !== screen));
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let socket, room = '', playerId = '', hostId = '', players = [], roomState = 'lobby';
let current = null, chains = [], mySubmitted = false, pending = false, ready = 0, total = 0;

function notify(text) {
  $('status-message').textContent = text;
  $('status-message').classList.toggle('hidden', !text);
}
function send(data) {
  if (socket?.readyState !== WebSocket.OPEN) {
    notify('The server is disconnected. Reload this page to connect again.');
    return false;
  }
  socket.send(JSON.stringify(data));
  return true;
}
function connect() {
  socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`);
  socket.onopen = () => {
    $('connection').textContent = 'Connected';
    document.querySelector('.online').classList.add('connected');
    renderPlayers();
  };
  socket.onclose = () => {
    $('connection').textContent = 'Offline';
    document.querySelector('.online').classList.remove('connected');
    notify('Connection lost. Reload to reconnect; an active game will continue with your missing turns marked.');
    renderPlayers(); updateSubmissionUI();
  };
  socket.onerror = () => { $('connection').textContent = 'Connection problem'; };
  socket.onmessage = ({data}) => {
    try { onMessage(JSON.parse(data)); }
    catch (error) { console.error(error); notify('A game update could not be displayed. Please reload to reconnect.'); }
  };
}

$('join-form').addEventListener('submit', event => {
  event.preventDefault();
  const name = $('player-name').value.trim();
  const code = $('room-code').value.trim().toUpperCase();
  if (name && code) { notify(''); send({type:'join', name, room:code}); }
});
$('copy-room').onclick = async () => {
  try {
    await navigator.clipboard.writeText(`${location.origin} · room ${room}`);
    $('copy-room').textContent = 'Copied!';
    setTimeout(() => $('copy-room').textContent = 'Copy invite code', 1500);
  } catch { notify(`Share ${location.origin} and room code ${room}`); }
};
$('start-game').onclick = () => send({type:'start'});
$('back-to-lobby').onclick = () => send({type:'lobby'});

function onMessage(message) {
  switch (message.type) {
    case 'joined':
      playerId = message.playerId; room = message.room;
      $('room-title').textContent = room; notify(''); show('lobby'); break;
    case 'roster':
      players = message.players; hostId = message.hostId; roomState = message.state;
      renderPlayers(); break;
    case 'notice': notify(message.text); break;
    case 'error':
      notify(message.text);
      if (pending) { pending = false; updateSubmissionUI(); }
      break;
    case 'phase': beginPhase(message); break;
    case 'accepted':
      if (!sameRound(message)) return;
      pending = false; mySubmitted = true; updateSubmissionUI(); break;
    case 'progress':
      if (!sameRound(message)) return;
      ready = message.ready; total = message.total; updateSubmissionUI(); break;
    case 'reveal':
      current = null; chains = message.chains; pending = false;
      renderResults(); show('results'); break;
    case 'lobby':
      current = null; chains = []; mySubmitted = false; pending = false;
      $('drawing-dialog').close();
      $('result-list').replaceChildren(); $('given-drawing').replaceChildren();
      $('given-prompt').textContent = ''; notify(''); show('lobby'); break;
  }
}
function sameRound(message) {
  return current && message.gameId === current.gameId && message.round === current.round;
}
function renderPlayers() {
  $('player-count').textContent = players.length;
  $('players').innerHTML = players.map(player => `<div class="player"><span class="avatar">${escapeHtml(player.name.slice(0,1).toUpperCase())}</span><span class="player-name">${escapeHtml(player.name)}</span>${player.id === playerId ? '<span class="you">YOU</span>' : ''}${player.id === hostId ? '<span class="you">HOST</span>' : ''}</div>`).join('') || '<p class="instruction">Waiting for players to join…</p>';
  $('game-players').innerHTML = players.map(player => `<span title="${escapeHtml(player.name)}">${escapeHtml(player.name.slice(0,1).toUpperCase())}</span>`).join('');
  const online = socket?.readyState === WebSocket.OPEN;
  $('start-game').disabled = !online || hostId !== playerId || players.length < 2 || roomState !== 'lobby';
  $('host-help').textContent = hostId === playerId ? 'Start when everyone has joined. Players cannot join during a game.' : 'The room host starts the game when everyone is ready.';
  $('back-to-lobby').disabled = !online || hostId !== playerId;
  $('replay-help').textContent = hostId === playerId ? 'Return everyone to the room to play again.' : 'The room host can return everyone to the room for another game.';
}
function beginPhase(message) {
  $('drawing-dialog').close();
  current = message; mySubmitted = false; pending = false; ready = 0; total = players.length;
  notify(''); show('game');
  const phase = current.phase;
  $('phase-label').textContent = `ROUND ${current.round} OF ${current.totalRounds}`;
  $('round-progress').max = current.totalRounds;
  $('round-progress').value = current.round;
  document.querySelectorAll('[data-phase-pill]').forEach(el => el.classList.toggle('active', el.dataset.phasePill === phase));
  $('phase-title').textContent = ({write:'Write a prompt', draw:'Draw it!', guess:'What is it?'})[phase];
  $('instruction').textContent = ({
    write:'Write a phrase for another player to draw. Everyone contributes to each chain once.',
    draw:'Draw only the description you received. Your drawing goes to the next player.',
    guess:'Describe the drawing. Your guess becomes the next drawing prompt.'
  })[phase];
  $('prompt-input').value = ''; $('guess-input').value = '';
  $('given-prompt').textContent = ''; $('given-drawing').replaceChildren();
  drawing = false;
  if (phase === 'draw') {
    $('given-prompt').textContent = current.assignment?.skipped
      ? 'The previous player disconnected without a description. Draw anything you like!'
      : current.assignment.text;
    clearCanvas();
  }
  if (phase === 'guess') {
    if (!current.assignment?.skipped && isDrawing(current.assignment?.text)) {
      const img = document.createElement('img');
      img.src = current.assignment.text; img.alt = 'The drawing assigned to you';
      $('given-drawing').append(img);
    } else {
      const placeholder = document.createElement('p');
      placeholder.className = 'placeholder';
      placeholder.textContent = 'The previous player disconnected without a drawing. Write a new description to continue the chain.';
      $('given-drawing').append(placeholder);
    }
  }
  updateSubmissionUI();
  if (phase === 'write') $('prompt-input').focus();
  if (phase === 'guess') $('guess-input').focus();
}
function updateSubmissionUI() {
  if (!current) return;
  ['write', 'draw', 'guess'].forEach(phase => {
    $(`${phase}-panel`).classList.toggle('hidden', phase !== current.phase || mySubmitted);
    $(`submit-${({write:'prompt', draw:'drawing', guess:'guess'})[phase]}`).disabled = pending || mySubmitted || socket?.readyState !== WebSocket.OPEN;
  });
  $('waiting').classList.toggle('hidden', !mySubmitted);
  $('submission-count').textContent = `${ready} / ${total} ready`;
  const remaining = Math.max(0, total - ready);
  $('waiting-text').textContent = remaining ? `Waiting for ${remaining} player${remaining === 1 ? '' : 's'}…` : 'Everyone is ready. Moving to the next round…';
  $('submit-status').textContent = pending ? 'Sending your work…' : '';
}
function submit(phase, text) {
  if (!current || phase !== current.phase || mySubmitted || pending) return;
  if (send({type:'submission', gameId:current.gameId, round:String(current.round), phase, text})) {
    pending = true; updateSubmissionUI();
  }
}
$('submit-prompt').onclick = () => {
  const value = $('prompt-input').value.trim();
  if (value) submit('write', value); else $('prompt-input').focus();
};
$('submit-drawing').onclick = () => submit('draw', canvas.toDataURL('image/png'));
$('submit-guess').onclick = () => {
  const value = $('guess-input').value.trim();
  if (value) submit('guess', value); else $('guess-input').focus();
};

const canvas = $('drawing'), ctx = canvas.getContext('2d');
let drawing = false;
function clearCanvas() { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); }
clearCanvas();
function point(event) {
  const rect = canvas.getBoundingClientRect();
  return {x:(event.clientX-rect.left)*canvas.width/rect.width, y:(event.clientY-rect.top)*canvas.height/rect.height};
}
canvas.addEventListener('pointerdown', event => {
  if (!current || current.phase !== 'draw' || pending || mySubmitted) return;
  drawing = true; canvas.setPointerCapture(event.pointerId);
  const p = point(event); ctx.beginPath(); ctx.moveTo(p.x, p.y);
  ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = $('ink-color').value; ctx.lineWidth = +$('ink-size').value;
  ctx.lineTo(p.x+.1, p.y+.1); ctx.stroke();
});
canvas.addEventListener('pointermove', event => {
  if (!drawing || pending || mySubmitted) return;
  const p = point(event); ctx.strokeStyle = $('ink-color').value; ctx.lineWidth = +$('ink-size').value;
  ctx.lineTo(p.x, p.y); ctx.stroke();
});
canvas.addEventListener('pointerup', () => drawing = false);
canvas.addEventListener('pointercancel', () => drawing = false);
$('clear-canvas').onclick = () => { if (!pending && !mySubmitted) clearCanvas(); };

function isDrawing(value) { return typeof value === 'string' && /^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(value); }
function renderResults() {
  const labels = {write:'Prompt', draw:'Drawing', guess:'Guess'};
  $('result-summary').textContent = `${chains.length} original prompts · ${chains[0]?.entries.length || 0} contributions per chain. Open a chain to follow its journey; select a drawing to enlarge it.`;
  $('result-list').innerHTML = chains.map((chain, index) => {
    const original = chain.entries[0];
    const prompt = original?.skipped ? 'Original prompt unavailable (player disconnected)' : original?.text;
    return `<details class="chain" ${index === 0 ? 'open' : ''}><summary><span class="chain-number">${index + 1}</span><span><small>${escapeHtml(chain.owner)}'s chain</small><strong>${escapeHtml(prompt)}</strong></span><span class="chain-toggle" aria-hidden="true">⌄</span></summary><ol class="chain-chat">${chain.entries.map(entry => `<li class="chat-entry ${entry.kind === 'draw' ? 'chat-drawing' : ''}"><div class="chat-meta"><b>${escapeHtml(entry.player)}${entry.playerId === playerId ? ' (you)' : ''}</b><span>Round ${entry.round} · ${labels[entry.kind]}</span></div><div class="chat-bubble">${entry.skipped ? '<p class="skipped">No submission — player disconnected.</p>' : entry.kind === 'draw' && isDrawing(entry.text) ? `<button class="drawing-preview" data-chain="${chain.id}" data-round="${entry.round}" aria-label="Enlarge drawing by ${escapeHtml(entry.player)}"><img loading="lazy" src="${entry.text}" alt="Drawing by ${escapeHtml(entry.player)} in round ${entry.round}"><span>Click to enlarge</span></button>` : `<p>${escapeHtml(entry.text)}</p>`}</div></li>`).join('')}</ol></details>`;
  }).join('');
  $('result-list').scrollTop = 0;
}
$('expand-chains').onclick = () => document.querySelectorAll('.chain').forEach(el => el.open = true);
$('collapse-chains').onclick = () => document.querySelectorAll('.chain').forEach(el => el.open = false);
$('result-list').addEventListener('click', event => {
  const button = event.target.closest('.drawing-preview');
  if (!button) return;
  const chain = chains.find(item => String(item.id) === button.dataset.chain);
  const entry = chain?.entries.find(item => String(item.round) === button.dataset.round);
  if (!entry || !isDrawing(entry.text)) return;
  $('expanded-drawing').src = entry.text;
  $('expanded-drawing').alt = `Drawing by ${entry.player}, round ${entry.round}`;
  $('drawing-caption').textContent = `${entry.player} · Round ${entry.round}`;
  $('drawing-dialog').showModal();
});
$('close-drawing').onclick = () => $('drawing-dialog').close();
$('drawing-dialog').addEventListener('close', () => $('expanded-drawing').removeAttribute('src'));
connect();
