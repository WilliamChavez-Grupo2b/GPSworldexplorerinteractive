// World Explorer GPS - frontend conectado al backend Spring Boot + MongoDB
const API = window.location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api';
let selectedRoute = null;
let currentTripId = null;
let planMap = null, planLine = null;
let navMap = null, navLine = null, navMarker = null;
let planMarkers = [], navMarkers = [], userMarker = null, userLocation = null;
const placeFields = {
  origin: { selected: null, timer: null, requestId: 0, activeIndex: -1, sessionToken: null },
  destination: { selected: null, timer: null, requestId: 0, activeIndex: -1, sessionToken: null }
};
const placePreviewMarkers = { origin: null, destination: null };

// ---------- Mapas (geolocalizacion) ----------
function addMapTiles(map) {
  const notice = document.createElement('div');
  notice.className = 'map-message hidden';
  notice.textContent = 'No se pudieron cargar las calles. Comprueba tu conexión a internet.';
  map.getContainer().appendChild(notice);

  let loadedTiles = 0;
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 19
  })
    .on('tileload', () => {
      loadedTiles += 1;
      notice.classList.add('hidden');
    })
    .on('tileerror', () => {
      if (loadedTiles === 0) notice.classList.remove('hidden');
    })
    .addTo(map);
}

function showMapUnavailable(id) {
  const container = document.getElementById(id);
  container.textContent = 'No se pudo cargar el mapa. Comprueba tu conexión e inténtalo de nuevo.';
  container.classList.add('map-unavailable');
}

function initPlanMap() {
  if (planMap) return;
  if (!window.L) {
    showMapUnavailable('map-plan');
    return;
  }
  planMap = L.map('map-plan').setView([-9.19, -75.02], 5);
  addMapTiles(planMap);
  setTimeout(() => planMap.invalidateSize(), 100);
}

function initNavMap() {
  if (navMap) return;
  if (!window.L) {
    showMapUnavailable('map-nav');
    return;
  }
  navMap = L.map('map-nav').setView([-9.19, -75.02], 5);
  addMapTiles(navMap);
  setTimeout(() => navMap.invalidateSize(), 100);
}

function getRouteLatLngs(route) {
  if (!Array.isArray(route.geometry)) return [];
  return route.geometry
    .filter(point => Array.isArray(point) && point.length >= 2 &&
      Number.isFinite(Number(point[0])) && Number.isFinite(Number(point[1])))
    .map(point => [Number(point[1]), Number(point[0])]);
}

function clearMarkers(markers) {
  markers.forEach(marker => marker.remove());
  markers.length = 0;
}

function showPlaceOnMap(fieldName, place) {
  initPlanMap();
  if (!planMap) return;
  if (placePreviewMarkers[fieldName]) placePreviewMarkers[fieldName].remove();

  const color = fieldName === 'origin' ? '#18764f' : '#d46b45';
  const popup = document.createElement('div');
  const label = document.createElement('strong');
  label.textContent = place.label;
  const coordinates = document.createElement('div');
  coordinates.textContent = `${place.lat.toFixed(5)}, ${place.lon.toFixed(5)}`;
  popup.append(label, coordinates);
  placePreviewMarkers[fieldName] = L.circleMarker([place.lat, place.lon], {
    radius: 8, color: '#fff', weight: 3, fillColor: color, fillOpacity: 1
  }).addTo(planMap).bindPopup(popup);

  const selectedMarkers = Object.values(placePreviewMarkers).filter(Boolean);
  if (selectedMarkers.length > 1) {
    planMap.fitBounds(L.featureGroup(selectedMarkers).getBounds().pad(0.35));
  } else {
    planMap.setView([place.lat, place.lon], Math.max(planMap.getZoom(), 12));
  }
}

function showSelectedCoordinates(inputId, place) {
  const output = document.getElementById(`${inputId}-coordinates`);
  output.textContent = place
    ? `Punto exacto: ${place.lat.toFixed(5)}, ${place.lon.toFixed(5)}`
    : '';
  output.hidden = !place;
}

function getPlacesSessionToken(field) {
  if (!field.sessionToken) {
    field.sessionToken = window.crypto && typeof window.crypto.randomUUID === 'function'
      ? window.crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
  return field.sessionToken;
}

function hideSuggestions(inputId) {
  const input = document.getElementById(inputId);
  const list = document.getElementById(`${inputId}-suggestions`);
  input.setAttribute('aria-expanded', 'false');
  input.removeAttribute('aria-activedescendant');
  list.hidden = true;
  placeFields[inputId].activeIndex = -1;
}

function renderPlaceSuggestions(inputId, places, requestId) {
  const input = document.getElementById(inputId);
  const list = document.getElementById(`${inputId}-suggestions`);
  const field = placeFields[inputId];
  if (requestId !== field.requestId) return;

  list.replaceChildren();
  field.activeIndex = -1;
  if (!places.length) {
    const empty = document.createElement('li');
    empty.className = 'suggestion-message';
    empty.textContent = 'No encontramos lugares con ese nombre.';
    list.appendChild(empty);
    list.hidden = false;
    input.setAttribute('aria-expanded', 'true');
    return;
  }

  places.forEach((place, index) => {
    const item = document.createElement('li');
    item.id = `${inputId}-suggestion-${index}`;
    item.setAttribute('role', 'option');
    item.setAttribute('aria-selected', 'false');
    const button = document.createElement('button');
    button.type = 'button';
    const name = document.createElement('span');
    name.className = 'suggestion-label';
    name.textContent = place.label;
    const coordinates = document.createElement('span');
    coordinates.className = 'suggestion-coordinates';
    coordinates.textContent = 'Selecciona para consultar el punto exacto';
    button.append(name, coordinates);
    button.addEventListener('pointerdown', event => event.preventDefault());
    button.addEventListener('click', async () => {
      button.disabled = true;
      coordinates.textContent = 'Consultando el punto exacto…';
      const params = new URLSearchParams({
        placeId: place.placeId,
        sessionToken: getPlacesSessionToken(field)
      });
      try {
        const selectedPlace = await requestJson(`/routes/geocode/place?${params.toString()}`);
        if (requestId !== field.requestId) return;
        field.selected = selectedPlace;
        field.sessionToken = null;
        input.value = selectedPlace.label;
        showSelectedCoordinates(inputId, selectedPlace);
        hideSuggestions(inputId);
        showPlaceOnMap(inputId, selectedPlace);
        input.focus();
      } catch (error) {
        if (requestId !== field.requestId) return;
        console.error('Error obteniendo coordenadas del lugar', error);
        coordinates.textContent = error.message;
        button.disabled = false;
      }
    });
    item.appendChild(button);
    list.appendChild(item);
  });

  list.hidden = false;
  input.setAttribute('aria-expanded', 'true');
}

function attachPlaceAutocomplete(inputId) {
  const input = document.getElementById(inputId);
  const list = document.getElementById(`${inputId}-suggestions`);
  const field = placeFields[inputId];

  input.addEventListener('input', () => {
    field.selected = null;
    showSelectedCoordinates(inputId, null);
    field.requestId += 1;
    clearTimeout(field.timer);
    if (placePreviewMarkers[inputId]) {
      placePreviewMarkers[inputId].remove();
      placePreviewMarkers[inputId] = null;
    }

    const query = input.value.trim();
    if (query.length < 3) {
      list.replaceChildren();
      hideSuggestions(inputId);
      return;
    }

    const requestId = field.requestId;
    const focus = inputId === 'destination' && placeFields.origin.selected
      ? placeFields.origin.selected
      : userLocation;
    const sessionToken = getPlacesSessionToken(field);
    list.replaceChildren();
    const loading = document.createElement('li');
    loading.className = 'suggestion-message';
    loading.textContent = 'Buscando lugares…';
    list.appendChild(loading);
    list.hidden = false;
    input.setAttribute('aria-expanded', 'true');
    field.timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams({ query, sessionToken });
        if (focus) {
          params.set('focusLat', focus.lat);
          params.set('focusLon', focus.lon);
        }
        const places = await requestJson(
          `/routes/geocode/suggestions?${params.toString()}`
        );
        renderPlaceSuggestions(inputId, places, requestId);
      } catch (error) {
        if (requestId !== field.requestId) return;
        console.error('Error buscando ubicaciones', error);
        list.replaceChildren();
        const failure = document.createElement('li');
        failure.className = 'suggestion-message';
        failure.textContent = error.message;
        list.appendChild(failure);
      }
    }, 300);
  });

  input.addEventListener('keydown', event => {
    const options = [...list.querySelectorAll('[role="option"]')];
    if (event.key === 'Escape') {
      hideSuggestions(inputId);
    } else if (options.length && event.key === 'ArrowDown') {
      event.preventDefault();
      field.activeIndex = (field.activeIndex + 1) % options.length;
    } else if (options.length && event.key === 'ArrowUp') {
      event.preventDefault();
      field.activeIndex = (field.activeIndex - 1 + options.length) % options.length;
    } else if (options.length && event.key === 'Enter' && field.activeIndex >= 0) {
      event.preventDefault();
      options[field.activeIndex].querySelector('button').click();
      return;
    } else {
      return;
    }

    options.forEach((option, index) => {
      const active = index === field.activeIndex;
      option.setAttribute('aria-selected', String(active));
      option.querySelector('button').classList.toggle('is-active', active);
    });
    if (field.activeIndex >= 0) {
      input.setAttribute('aria-activedescendant', options[field.activeIndex].id);
    } else {
      input.removeAttribute('aria-activedescendant');
    }
  });

  input.addEventListener('blur', () => {
    setTimeout(() => hideSuggestions(inputId), 150);
  });
}

function addRouteMarkers(map, route, markers) {
  clearMarkers(markers);
  const points = getRouteLatLngs(route);
  const start = Number.isFinite(route.startLat) && Number.isFinite(route.startLon)
    ? [route.startLat, route.startLon]
    : points[0];
  const end = Number.isFinite(route.endLat) && Number.isFinite(route.endLon)
    ? [route.endLat, route.endLon]
    : points[points.length - 1];
  if (!start || !end) return points;
  if (start.every(Number.isFinite) && end.every(Number.isFinite)) {
    markers.push(L.circleMarker(start, {
      radius: 7, color: '#fff', weight: 3, fillColor: '#18764f', fillOpacity: 1
    }).bindPopup('Punto de partida').addTo(map));
    markers.push(L.circleMarker(end, {
      radius: 7, color: '#fff', weight: 3, fillColor: '#d46b45', fillOpacity: 1
    }).bindPopup('Destino').addTo(map));
  }
  return points;
}

function drawRouteOnPlanMap(route) {
  initPlanMap();
  if (!planMap) return;
  Object.keys(placePreviewMarkers).forEach(key => {
    if (placePreviewMarkers[key]) placePreviewMarkers[key].remove();
    placePreviewMarkers[key] = null;
  });
  if (planLine) planLine.remove();
  const points = addRouteMarkers(planMap, route, planMarkers);
  if (points.length > 1) {
    planLine = L.polyline(points, {
      color: '#18764f', weight: 5, opacity: .9, lineCap: 'round', lineJoin: 'round'
    }).addTo(planMap);
    planMap.fitBounds(planLine.getBounds().pad(0.15));
  } else if (points.length === 1) {
    planMap.setView(points[0], 12);
  }
}

function drawRouteOnNavMap(route) {
  initNavMap();
  if (!navMap) return;
  if (navLine) navLine.remove();
  const points = addRouteMarkers(navMap, route, navMarkers);
  if (points.length > 1) {
    navLine = L.polyline(points, {
      color: '#18764f', weight: 5, opacity: .9, lineCap: 'round', lineJoin: 'round'
    }).addTo(navMap);
    navMap.fitBounds(navLine.getBounds().pad(0.2));
  } else if (points.length === 1) {
    navMap.setView(points[0], 12);
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
     placeFields.origin.requestId += 1;
     clearTimeout(placeFields.origin.timer);
     placeFields.origin.sessionToken = null;
     originInput.value = `${userLocation.lat.toFixed(5)}, ${userLocation.lon.toFixed(5)}`;
     placeFields.origin.selected = {
       lat: userLocation.lat,
       lon: userLocation.lon,
       label: originInput.value
     };
     showSelectedCoordinates('origin', placeFields.origin.selected);
     hideSuggestions('origin');
     initPlanMap();
     if (planMap) {
       if (placePreviewMarkers.origin) placePreviewMarkers.origin.remove();
       placePreviewMarkers.origin = null;
       if (userMarker) userMarker.remove();
       userMarker = L.circleMarker([userLocation.lat, userLocation.lon], {
         radius: 8, color: '#fff', weight: 3, fillColor: '#18764f', fillOpacity: 1
       }).addTo(planMap).bindPopup('Tu ubicación actual').openPopup();
       planMap.setView([userLocation.lat, userLocation.lon], 14);
     }
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
    const activeTab = document.getElementById(btn.dataset.tab);
    activeTab.classList.add('active');
    setTimeout(() => {
      if (btn.dataset.tab === 'planificador' && planMap) planMap.invalidateSize();
      if (btn.dataset.tab === 'navegacion' && navMap) navMap.invalidateSize();
    }, 100);
  });
});

attachPlaceAutocomplete('origin');
attachPlaceAutocomplete('destination');
initPlanMap();

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
  const start = placeFields.origin.selected;
  const end = placeFields.destination.selected;
  if (!start || !end) {
    errorEl.textContent = 'Selecciona el origen y el destino desde la lista de ubicaciones.';
    return;
  }
  const body = {
    origin,
    destination,
    originLat: start.lat,
    originLon: start.lon,
    destinationLat: end.lat,
    destinationLon: end.lon,
    avoidTolls: document.getElementById('avoidTolls').checked,
    preferSafeRoutes: document.getElementById('preferSafe').checked
  };
  const routes = await post('/routes/plan', body, errorEl);
  if (!routes) return;
  const list = document.getElementById('route-list');
  list.innerHTML = '';
  if (routes.length === 0) {
    list.textContent = 'No encontramos rutas para esos lugares. Revisa el origen y el destino.';
    return;
  }
  drawRouteOnPlanMap(routes[0]);
  routes.forEach((r, i) => {
    const div = document.createElement('div');
    div.className = 'route-card';
    const heading = document.createElement('h3');
    heading.textContent = r.name;
    div.appendChild(heading);
    if (i === 0) {
      const recommended = document.createElement('span');
      recommended.className = 'route-recommended';
      recommended.textContent = '★ Recomendada';
      div.appendChild(recommended);
    }
    const details = document.createElement('p');
    details.textContent = `Distancia: ${r.distanceKm} km · Duración: ${r.durationMin} min · Tráfico: ${Math.round(r.trafficLevel * 100)}% · Riesgo: ${Math.round(r.riskLevel * 100)}% · Clima: ${r.weather} · Puntaje: ${r.score}`;
    div.appendChild(details);
    const selectButton = document.createElement('button');
    selectButton.type = 'button';
    selectButton.className = 'button';
    selectButton.textContent = 'Seleccionar y navegar';
    div.appendChild(selectButton);
    list.appendChild(div);
    selectButton.addEventListener('click', () => {
      selectedRoute = r;
      currentTripId = null;
      document.getElementById('nav-empty').classList.add('hidden');
      document.getElementById('nav-panel').classList.remove('hidden');
      switchTab('navegacion');
      drawRouteOnPlanMap(selectedRoute);
      drawRouteOnNavMap(selectedRoute);
    });
  });
});

document.getElementById('add-route').addEventListener('click', () => {
  if (!selectedRoute) return;
  const originInput = document.getElementById('origin');
  const destinationInput = document.getElementById('destination');
  const nextOrigin = {
    lat: selectedRoute.endLat,
    lon: selectedRoute.endLon,
    label: selectedRoute.destination
  };
  originInput.value = nextOrigin.label;
  placeFields.origin.requestId += 1;
  clearTimeout(placeFields.origin.timer);
  placeFields.origin.sessionToken = null;
  placeFields.origin.selected = nextOrigin;
  showSelectedCoordinates('origin', nextOrigin);
  destinationInput.value = '';
  placeFields.destination.requestId += 1;
  clearTimeout(placeFields.destination.timer);
  placeFields.destination.sessionToken = null;
  placeFields.destination.selected = null;
  showSelectedCoordinates('destination', null);
  document.getElementById('route-list').replaceChildren();
  document.getElementById('plan-error').textContent = '';
  if (planLine) {
    planLine.remove();
    planLine = null;
  }
  clearMarkers(planMarkers);
  hideSuggestions('origin');
  hideSuggestions('destination');
  switchTab('planificador');
  showPlaceOnMap('origin', nextOrigin);
  destinationInput.focus();
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
  err.textContent = '';
  const finishButton = document.getElementById('nav-finish');
  finishButton.disabled = true;
  const finishedTrip = await post(`/routes/trips/${currentTripId}/finish`, {}, err);
  if (!finishedTrip) {
    finishButton.disabled = false;
    return;
  }

  document.getElementById('nav-update').disabled = true;
  currentTripId = null;
  if (selectedRoute) drawRouteOnNavMap(selectedRoute);
  await loadHistory();
  alert('Viaje finalizado y guardado en el historial.');
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
  const body = document.getElementById('history-body');
  const message = document.getElementById('history-message');
  body.replaceChildren();
  message.className = '';
  message.textContent = '';

  let trips;
  try {
    trips = await requestJson('/history/trips');
  } catch (error) {
    message.className = 'error';
    message.textContent = `No se pudo cargar el historial: ${error.message}`;
    return;
  }

  if (!Array.isArray(trips)) {
    message.className = 'error';
    message.textContent = 'El servidor devolvió un historial no válido.';
    return;
  }
  if (trips.length === 0) {
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = 10;
    cell.textContent = 'Todavía no hay viajes guardados.';
    row.appendChild(cell);
    body.appendChild(row);
    return;
  }

  for (const t of trips) {
    let alerts = [];
    let alertsError = null;
    try {
      const result = await requestJson(`/history/trips/${encodeURIComponent(t.id)}/alerts`);
      if (!Array.isArray(result)) throw new Error('El servidor devolvió alertas no válidas.');
      alerts = result;
    } catch (error) {
      alertsError = error;
    }

    const tr = document.createElement('tr');
    const addCell = value => {
      const cell = document.createElement('td');
      cell.textContent = value == null || value === '' ? '-' : String(value);
      tr.appendChild(cell);
    };
    const formatDate = value => {
      if (!value) return '-';
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString();
    };

    addCell(formatDate(t.startedAt));
    addCell(formatDate(t.finishedAt));
    addCell(t.origin);
    addCell(t.destination);
    addCell(t.routeName);
    addCell(Number.isFinite(Number(t.distanceKm)) ? `${Number(t.distanceKm).toFixed(1)} km` : '-');
    addCell(Number.isFinite(Number(t.durationMin)) ? `${Number(t.durationMin).toFixed(1)} min` : '-');
    addCell(t.status);
    addCell(alertsError ? 'No disponible' : alerts.length);

    const detailCell = document.createElement('td');
    const details = document.createElement('details');
    details.className = 'history-details';
    const summary = document.createElement('summary');
    summary.textContent = 'Ver datos';
    details.appendChild(summary);
    const list = document.createElement('ul');
    const detailLines = [
      `ID del viaje: ${t.id || '-'}`,
      `ID de usuario: ${t.userId || '-'}`,
      `ID de ruta: ${t.routeOptionId || '-'}`,
      `Condiciones: ${Array.isArray(t.weatherConditions) && t.weatherConditions.length
        ? t.weatherConditions.join(', ')
        : 'Sin datos'}`
    ];
    if (Array.isArray(t.events) && t.events.length) {
      detailLines.push(...t.events.map(event => `Evento: ${event}`));
    } else {
      detailLines.push('Eventos: Sin eventos registrados');
    }
    if (alertsError) {
      detailLines.push(`Alertas: No se pudieron cargar (${alertsError.message})`);
    } else if (alerts.length) {
      detailLines.push(...alerts.map(alert =>
        `Alerta [${alert.type}]: ${alert.message} (${formatDate(alert.createdAt)})`));
    } else {
      detailLines.push('Alertas: Ninguna');
    }
    detailLines.forEach(line => {
      const item = document.createElement('li');
      item.textContent = line;
      list.appendChild(item);
    });
    details.appendChild(list);
    detailCell.appendChild(details);
    tr.appendChild(detailCell);
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
async function requestJson(path, options = {}) {
  let response;
  try {
    response = await fetch(API + path, options);
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error('No hay conexión con el servidor local. Inicia World Explorer GPS e inténtalo de nuevo.');
    }
    throw error;
  }

  if (!response.ok) {
    let payload = null;
    try {
      payload = await response.json();
    } catch (error) {
      // The response can be an empty body or non-JSON server error.
    }
    if ((response.status === 401 || response.status === 403) && !payload?.detail) {
      throw new Error('Google rechazó la clave. Verifica las API habilitadas, la facturación y sus restricciones.');
    }
    if (response.status === 429) {
      throw new Error('Google limitó temporalmente las búsquedas. Intenta de nuevo más tarde.');
    }
    throw new Error(payload?.detail || payload?.message || `Error del servidor (${response.status}).`);
  }
  if (response.status === 204) return null;
  return response.json();
}

async function post(path, body, errEl) {
  try {
    return await requestJson(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  } catch (e) {
    if (errEl) { errEl.className = 'error'; errEl.textContent = 'Error: ' + e.message; }
    return null;
  }
}

async function get(path) {
  try {
    return await requestJson(path);
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
  setTimeout(() => {
    if (name === 'planificador' && planMap) planMap.invalidateSize();
    if (name === 'navegacion' && navMap) navMap.invalidateSize();
  }, 100);
}
