document.addEventListener('DOMContentLoaded', () => {

  // --- Registration handler ---
  const registerForm = document.getElementById("registerForm");
  if (registerForm) {
    registerForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const username = document.getElementById("username").value.trim();
      const email = document.getElementById("email").value.trim();
      const password = document.getElementById("password").value.trim();

      const msgDiv = document.getElementById("msg");
      try {
        const res = await fetch("/api/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, email, password })
        });

        if (res.ok) {
          msgDiv.innerText = "✅ Registration successful! Redirecting to login...";
          msgDiv.className = "text-success";
          setTimeout(() => window.location.href = "/login.html", 1500);
        } else {
          const err = await res.text();
          msgDiv.innerText = "❌ " + err;
          msgDiv.className = "text-danger";
        }
      } catch (error) {
        msgDiv.innerText = "❌ Error connecting to server.";
      }
    });
  }

  const loginForm = document.getElementById("loginForm");
  if (loginForm) {
    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();

      // Use "username" to match LoginRequest
      const username = document.getElementById("who").value.trim();
      const password = document.getElementById("password").value.trim();

      const msgDiv = document.getElementById("msg");
      try {
        const res = await fetch("/api/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password }) // <-- match DTO
        });

        const data = await res.json();

        if (res.ok && data.token) {
          // remove "Bearer " prefix if your frontend only wants token
          localStorage.setItem("aetheris_token", data.token.replace("Bearer ", ""));
          msgDiv.innerText = "✅ Login successful! Redirecting...";
          msgDiv.className = "text-success";
          setTimeout(() => window.location.href = "/dashboard.html", 1000);
        } else {
          msgDiv.innerText = "❌ Invalid credentials.";
          msgDiv.className = "text-danger";
        }
      } catch (error) {
        msgDiv.innerText = "❌ Error connecting to server.";
        msgDiv.className = "text-danger";
      }
    });
  }


});
