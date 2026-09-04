const api = async (path, options = {}) => {
  const response = await fetch(`/api${path}`, { credentials: "same-origin", headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.error || `Request failed (${response.status})`);
  return body;
};

const authPanel = document.querySelector("#auth-panel");
const dashboard = document.querySelector("#dashboard");
const authError = document.querySelector("#auth-error");
const vmError = document.querySelector("#vm-error");
let socket;
let peer;
let inputActive = false;

async function refresh() {
  const vms = await api("/vms");
  document.querySelector("#vm-list").replaceChildren(...vms.map(vm => {
    const row = document.createElement("article");
    row.className = "card vm";
    row.innerHTML = `<span><strong>${vm.name}</strong> · ${vm.status}</span>`;
    const actions = document.createElement("span");
    const connect = document.createElement("button");
    connect.textContent = "Connect";
    connect.onclick = () => connectVm(vm);
    const remove = document.createElement("button");
    remove.textContent = "Delete";
    remove.onclick = async () => { await api(`/vms/${vm.id}`, { method: "DELETE" }); await refresh(); };
    actions.append(connect, remove); row.append(actions); return row;
  }));
}

function send(type, values = {}) {
  if (inputActive && socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type, ...values }));
}

function setInputActive(active) {
  inputActive = active;
  document.querySelector("#stream").classList.toggle("input-active", active);
}

async function connectVm(vm) {
  document.querySelector("#viewer").hidden = false;
  document.querySelector("#viewer-name").textContent = vm.name;
  socket?.close();
  socket = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/vms/${vm.id}/input`);
  const video = document.querySelector("#stream");
  video.tabIndex = 0;
  video.onclick = async () => {
    try {
      await video.requestPointerLock();
    } catch (_) {
      setInputActive(false);
    }
  };
  video.onmousemove = event => send("move", { deltaX: event.movementX, deltaY: event.movementY });
  video.onmousedown = event => { event.preventDefault(); send("button", { button: event.button, pressed: true }); };
  video.onmouseup = event => send("button", { button: event.button, pressed: false });
  await connectWhep(video, vm.streamUrl);
}

async function connectWhep(video, url) {
  peer?.close();
  peer = new RTCPeerConnection();
  peer.addTransceiver("video", { direction: "recvonly" });
  peer.ontrack = event => { video.srcObject = event.streams[0]; };
  const offer = await peer.createOffer();
  await peer.setLocalDescription(offer);
  const answer = await fetch(url, { method: "POST", headers: { "Content-Type": "application/sdp" }, body: offer.sdp });
  if (!answer.ok) throw new Error("Could not connect to the video stream");
  await peer.setRemoteDescription({ type: "answer", sdp: await answer.text() });
}

document.querySelector("#auth-form").onsubmit = async event => {
  event.preventDefault(); authError.textContent = "";
  try { await api("/auth/sign-in", { method: "POST", body: JSON.stringify({ username: username.value, password: password.value }) }); authPanel.hidden = true; dashboard.hidden = false; await refresh(); }
  catch (error) { authError.textContent = error.message; }
};
document.querySelector("#sign-up").onclick = async () => {
  authError.textContent = "";
  try { await api("/auth/sign-up", { method: "POST", body: JSON.stringify({ username: username.value, password: password.value }) }); authError.textContent = "Account created. You can sign in now."; }
  catch (error) { authError.textContent = error.message; }
};
document.querySelector("#create-form").onsubmit = async event => {
  event.preventDefault(); vmError.textContent = "";
  try { await api("/vms", { method: "POST", body: JSON.stringify({ name: document.querySelector("#vm-name").value }) }); await refresh(); }
  catch (error) { vmError.textContent = error.message; }
};
document.querySelector("#sign-out").onclick = async () => { await api("/auth/sign-out", { method: "POST" }); location.reload(); };
document.querySelector("#close-viewer").onclick = () => { document.exitPointerLock(); setInputActive(false); socket?.close(); peer?.close(); document.querySelector("#viewer").hidden = true; };
document.addEventListener("pointerlockchange", () => setInputActive(document.pointerLockElement === document.querySelector("#stream")));
document.addEventListener("keydown", event => { if (inputActive) { event.preventDefault(); send("key", { keyCode: event.keyCode, pressed: true }); } });
document.addEventListener("keyup", event => { if (inputActive) { event.preventDefault(); send("key", { keyCode: event.keyCode, pressed: false }); } });
api("/auth/me").then(() => { authPanel.hidden = true; dashboard.hidden = false; refresh(); }).catch(() => {});
