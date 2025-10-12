document.addEventListener('DOMContentLoaded', async () => {
  const token = localStorage.getItem('aetheris_token');
  if (!token) {
    window.location.href = '/login.html';
    return;
  }

  // Helper to include JWT token in fetch
  const authFetch = (url, options = {}) => {
    const headers = {
      ...(options.headers || {}),
      'Authorization': 'Bearer ' + token
    };
    return fetch(url, { ...options, headers });
  };

  // Fade in animation
  document.body.classList.add('fadeIn');

  try {
    // Dashboard data
    const res = await authFetch('/api/secure/dashboard');
    if (res.status === 401) {
      localStorage.removeItem('aetheris_token');
      window.location.href = '/login.html';
      return;
    }
    const data = await res.json();
    document.getElementById('hello').innerText = `Hello, ${data.username}`;
    document.getElementById('capital').innerText = `Capital: ₹${data.capital} • Index: ${data.preferredIndex}`;

    const tradesList = document.getElementById('tradesList');
    tradesList.innerHTML = '';
    (data.recentTrades || []).forEach(t => {
      const el = document.createElement('div');
      el.innerHTML = `<strong>${t.symbol}</strong> • ₹${t.price} • QTY ${t.qty} • P/L ₹${t.pl}`;
      tradesList.appendChild(el);
    });

    // Chart.js glowing line
	// --- Live Index Chart Section ---
	const ctx = document.getElementById('chart').getContext('2d');
	let liveChart;
	let refreshInterval;

	async function loadIndexData(symbol, showNotice = true) {
	  try {
		const res = await fetch(`http://localhost:3001/index/${symbol}`);
	    if (!res.ok) throw new Error('Bad response from server');
	    const json = await res.json();

	    // NSE data can be "grapthData" or "grapthData1" depending on index type
	    const points = json.grapthData || json.grapthData1 || [];

	    if (!points.length) {
	      console.warn('⚠️ No chart data returned for', symbol);
	      document.getElementById('marketStatus').innerText = `⚠️ No Data Available for ${symbol}`;
	      return;
	    }

	    const sliced = points.slice(-50);
	    const labels = sliced.map(p => new Date(p[0]).toLocaleTimeString());
	    const values = sliced.map(p => p[1]);

	    if (liveChart) liveChart.destroy();
	    liveChart = new Chart(ctx, {
	      type: 'line',
	      data: {
	        labels,
	        datasets: [{
	          label: `${symbol} Live`,
	          data: values,
	          fill: true,
	          backgroundColor: 'rgba(43,212,248,0.15)',
	          borderColor: 'rgba(43,212,248,1)',
	          borderWidth: 2,
	          tension: 0.3,
	          pointRadius: 0
	        }]
	      },
	      options: {
	        animation: false,
	        plugins: { legend: { display: false } },
	        scales: {
	          x: { display: false },
	          y: { display: true, ticks: { color: '#ccc' } }
	        }
	      }
	    });

	    document.getElementById('marketStatus').innerText =
	      isMarketOpen() ? 'Market Live 🟢' : 'Market Closed 🔴';

	    if (showNotice)
	      console.log(`✅ Chart updated for ${symbol} at ${new Date().toLocaleTimeString()}`);
	  } catch (err) {
	    console.error('❌ Error loading market data:', err);
	    document.getElementById('marketStatus').innerText = '⚠️ Unable to load market data.';
	  }
	}

	// Market time window check (09:15–15:30)
	function isMarketOpen() {
	  const now = new Date();
	  const current = now.getHours() * 60 + now.getMinutes();
	  const openTime = 9 * 60 + 15;   // 555
	  const closeTime = 15 * 60 + 30; // 930
	  return current >= openTime && current <= closeTime;
	}

	// Handles refresh behavior
	function manageChartRefresh() {
	  const symbol = document.getElementById('indexType').value;
	  if (refreshInterval) clearInterval(refreshInterval);

	  if (isMarketOpen()) {
	    console.log('📈 Market open — updating every 5s');
	    loadIndexData(symbol, false);
	    refreshInterval = setInterval(() => loadIndexData(symbol, false), 5000);
	  } else {
	    console.log('⏸ Market closed — showing last snapshot');
	    loadIndexData(symbol, false);
	  }
	}

	// Initial load
	manageChartRefresh();

	// When user changes index
	document.getElementById('indexType').addEventListener('change', manageChartRefresh);

	// Recheck every 1 minute (if market opens or closes)
	setInterval(() => {
	  manageChartRefresh();
	}, 60000);

  } catch (err) {
    console.error('Error loading dashboard:', err);
  }

  // Logout
  document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('aetheris_token');
    window.location.href = '/login.html';
  });

  // Demo Trade
  document.getElementById('placeBtn').addEventListener('click', () => {
    alert('Demo trade placed (frontend only).');
  });

  // Bot actions
  const fetchBot = async (url, payload) => {
    const res = await authFetch(url, {
      method: payload ? 'POST' : 'GET',
      headers: { 'Content-Type': 'application/json' },
      body: payload ? JSON.stringify(payload) : null
    });
    alert(await res.text());
  };

  document.getElementById('saveConfigBtn').addEventListener('click', () => {
    const payload = {
      indexType: document.getElementById('indexType').value,
      capitalAmount: parseFloat(document.getElementById('capitalAmount').value),
      targetPercent: parseFloat(document.getElementById('targetPercent').value),
      stoplossPercent: parseFloat(document.getElementById('stoplossPercent').value)
    };
    fetchBot('/api/bot/config', payload);
  });

  document.getElementById('startBotBtn').addEventListener('click', () => fetchBot('/api/bot/start'));
  document.getElementById('stopBotBtn').addEventListener('click', () => fetchBot('/api/bot/stop'));

  // Status Poll
  setInterval(async () => {
    try {
      const res = await authFetch('/api/bot/status');
      if (!res.ok) return;
      const status = await res.json();
      document.getElementById('positionStatus').innerText = status.positionTaken ? 'Position Active' : 'Idle';
      document.getElementById('monitorStatus').innerText = status.monitoring ? 'Monitoring' : 'No';
      document.getElementById('cooldown').innerText = (status.cooldownMillis / 1000).toFixed(0) + 's';
    } catch (err) {
      console.error('Error fetching bot status:', err);
    }
  }, 2000);
});
