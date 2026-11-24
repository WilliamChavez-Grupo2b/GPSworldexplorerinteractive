// World Explorer GPS - frontend conectado al backend Spring Boot + MongoDB
const API = 'http://localhost:8080/api';
let selectedRoute = null;
let currentTripId = null;
let planMap = null, planLine = null;
let navMap = null, navLine = null, navMarker = null;
let userLocation = null;

// ---------- Mapas (geolocalizacion) ----------
function initPlanMap() {
  if (planMap) return;
  planMap = L.map('map-plan').setView([-12.05, -77.04], 6);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap'
  }).addTo(planMap);
  setTimeout(() => planMap.invalidateSize(), 100);
}

function initNavMap() {
  if (navMap) return;
  navMap = L.map('map-nav').setView([-12.05, -77.04], 12);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap'
  }).addTo(navMap);
  setTimeout(() => navMap.invalidateSize(), 100);
}

function drawRouteOnPlanMap(route) {
  initPlanMap();
  if (planLine) planLine.remove();
  if (route.geometry && route.geometry.length > 1) {
    planLine = L.polyline(route.geometry, { color: '#2f7dfb', weight: 5 }).addTo(planMap);
    planMap.fitBounds(planLine.getBounds().pad(0.15));
  }
}

function drawRouteOnNavMap(route) {
  initNavMap();
  if (navLine) navLine.remove();
  if (route.geometry && route.geometry.length > 1) {
    navLine = L.polyline(route.geometry, { color: '#2f7dfb', weight: 5 }).addTo(navMap);
    navMap.fitBounds(navLine.getBounds().pad(0.2));
  }
}

// Botón "Mi ubicación": geolocalización del navegador
 document.getElementById('use-my-location').addEventListener('click', () => {
   if (!navigator.geolocation) {
     document.getElementById('plan-error').textContent = 'Tu navegador no soporta geolocalización.';
     return;
   }
   navigator.geolocation.getCurrentPosition(async (pos) => {
     userLocation = { lat: pos.coords.latitude, lon: pos.coords.longitude };
     const originInput = document.getElementById('origin');
     originInput.value = `${userLocation.lat.toFixed(5)}, ${userLocation.lon.toFixed(5)}`;
     initPlanMap();
     L.marker([userLocation.lat, userLocation.lon]).addTo(planMap)
       .bindPopup('📍 Tu ubicación actual').openPopup();
     planMap.setView([userLocation.lat, userLocation.lon], 14);
   }, (err) => {
     document.getElementById('plan-error').textContent = 'No se pudo obtener tu ubicación: ' + err.message;
   }, { enableHighAccuracy: true, timeout: 10000 });
 });

// ---------- Navegación de pestañas ----------
document.querySelectorAll('nav button').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(btn.dataset.tab).classList.add('active');
  });
});

// ---------- Planificador (RF-01, RF-02) ----------
document.getElementById('plan-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorEl = document.getElementById('plan-error');
  errorEl.textContent = '';
  const origin = document.getElementById('origin').value.trim();
  const destination = document.getElementById('destination').value.trim();
  if (!origin || !destination) {
    errorEl.textContent = 'Origen y destino son obligatorios.';
    return;
  }
  const body = {
    origin,
    destination,
    avoidTolls: document.getElementById('avoidTolls').checked,
    preferSafeRoutes: document.getElementById('preferSafe').checked
  };
  const routes = await post('/routes/plan', body, errorEl);
  if (!routes) return;
  const list = document.getElementById('route-list');
  list.innerHTML = '';
  drawRouteOnPlanMap(routes[0]);
  routes.forEach((r, i) => {
    const div = document.createElement('div');
    div.className = 'route-card';
    div.innerHTML = `
      <strong>${r.name}</strong> ${i === 0 ? '⭐ recomendada' : ''}<br>
      Distancia: ${r.distanceKm} km · Duración: ${r.durationMin} min<br>
      Tráfico: ${Math.round(r.trafficLevel * 100)}% · Riesgo: ${Math.round(r.riskLevel * 100)}% · Clima: ${r.weather}<br>
      Puntaje: ${r.score}
      <br><button data-route='${JSON.stringify(r)}'>Seleccionar y navegar</button>`;
    list.appendChild(div);
  });
  list.querySelectorAll('button[data-route]').forEach(b => {
    b.addEventListener('click', () => {
      selectedRoute = JSON.parse(b.dataset.route);
      currentTripId = null;
      drawRouteOnNavMap(selectedRoute);
      document.getElementById('nav-empty').classList.add('hidden');
      document.getElementById('nav-panel').classList.remove('hidden');
      switchTab('navegacion');
    });
  });
});

// ---------- Navegación en tiempo real (RF-03, RF-04) ----------
document.getElementById('nav-start').addEventListener('click', async () => {
  const err = document.getElementById('plan-error');
  const trip = await post('/routes/trips', {
    origin: selectedRoute.origin,
    destination: selectedRoute.destination,
    routeOptionId: selectedRoute.id,
    routeName: selectedRoute.name,
    distanceKm: selectedRoute.distanceKm,
    durationMin: selectedRoute.durationMin,
    status: 'ACTIVO'
  }, err);
  if (trip) {
    currentTripId = trip.id;
    document.getElementById('alert-trip').value = trip.id;
    document.getElementById('nav-update').disabled = false;
    document.getElementById('nav-finish').disabled = false;
    updateNavigation(selectedRoute);
  }
});

document.getElementById('nav-update').addEventListener('click', () => {
  if (currentTripId) updateNavigation(selectedRoute);
});

document.getElementById('nav-finish').addEventListener('click', async () => {
  if (!currentTripId) return;
  const err = document.getElementById('plan-error');
  await post(`/routes/trips/${currentTripId}/finish`, {}, err);
  document.getElementById('nav-update').disabled = true;
  document.getElementById('nav-finish').disabled = true;
  alert('Viaje completado y guardado en el historial.');
  currentTripId = null;
});

async function updateNavigation(route) {
  const err = document.getElementById('plan-error');
  const state = await post(`/routes/trips/${currentTripId}/update`, route, err);
  if (!state) return;
  document.getElementById('nav-speed').textContent = state.speedKmh;
  document.getElementById('nav-turn').textContent = state.nextTurn;
  document.getElementById('nav-remaining').textContent = state.remainingKm;
  document.getElementById('nav-eta').textContent = state.etaMin;
  document.getElementById('nav-pos').textContent = state.lat.toFixed(4) + ', ' + state.lon.toFixed(4);
  // Mover marcador de posición en el mapa de navegación
  if (navMap) {
    if (!navMarker) {
      navMarker = L.circleMarker([state.lat, state.lon], {
        radius: 8, color: '#e74c3c', fillColor: '#e74c3c', fillOpacity: 1
      }).addTo(navMap);
    } else {
      navMarker.setLatLng([state.lat, state.lon]);
    }
    navMap.setView([state.lat, state.lon], navMap.getZoom());
  }
  if (state.arrived) {
    alert('¡Has llegado a tu destino!');
    document.getElementById('nav-update').disabled = true;
  }
}

// ---------- Alertas (RF-05) ----------
document.getElementById('alert-load').addEventListener('click', async () => {
  const tripId = document.getElementById('alert-trip').value.trim();
  if (!tripId) return;
  const alerts = await get(`/history/trips/${tripId}/alerts`);
  const list = document.getElementById('alert-list');
  list.innerHTML = '';
  if (!alerts || alerts.length === 0) {
    list.innerHTML = '<li>Sin alertas registradas.</li>';
    return;
  }
  alerts.forEach(a => {
    const li = document.createElement('li');
    li.textContent = `[${a.type}] ${a.message} — ${new Date(a.createdAt).toLocaleString()}`;
    list.appendChild(li);
  });
});

// ---------- Historial (RF-06) ----------
document.getElementById('history-load').addEventListener('click', loadHistory);
async function loadHistory() {
  const trips = await get('/history/trips');
  const body = document.getElementById('history-body');
  body.innerHTML = '';
  if (!trips) return;
  for (const t of trips) {
    const alerts = await get(`/history/trips/${t.id}/alerts`) || [];
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${t.startedAt ? new Date(t.startedAt).toLocaleString() : '-'}</td>
      <td>${t.origin}</td><td>${t.destination}</td><td>${t.routeName || '-'}</td>
      <td>${t.status}</td><td>${alerts.length}</td>`;
    body.appendChild(tr);
  }
}

// ---------- Preferencias (RF-07) ----------
document.getElementById('pref-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const msg = document.getElementById('pref-msg');
  const userId = document.getElementById('pref-user').value.trim();
  if (!userId) { msg.className = 'error'; msg.textContent = 'Ingresa el userId.'; return; }
  const pref = await post(`/preferences/${userId}`, {
    userId,
    language: document.getElementById('pref-lang').value,
    distanceUnit: document.getElementById('pref-unit').value,
    voiceVolume: parseInt(document.getElementById('pref-vol').value),
    alertType: document.getElementById('pref-alert').value
  }, msg);
  if (pref) { msg.className = 'ok'; msg.textContent = 'Preferencias guardadas en MongoDB ✔'; }
});

// ---------- Helpers HTTP ----------
async function post(path, body, errEl) {
  try {
    const res = await fetch(API + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(await res.text());
    return await res.json();
  } catch (e) {
    if (errEl) { errEl.className = 'error'; errEl.textContent = 'Error: ' + e.message; }
    return null;
  }
}

async function get(path) {
  try {
    const res = await fetch(API + path);
    if (!res.ok) throw new Error(await res.text());
    return await res.json();
  } catch (e) {
    console.error('Error consultando ' + path, e);
    return null;
  }
}

function switchTab(name) {
  document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.querySelector(`nav button[data-tab="${name}"]`).classList.add('active');
  document.getElementById(name).classList.add('active');
}
