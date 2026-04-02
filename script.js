const overviewData = {
  userName: "Justin",
  subtitle: "Here is your health overview for today",

  stats: [
    { icon: "📅", value: 2, label: "Upcoming" },
    { icon: "💬", value: 1, label: "Unread" },
    { icon: "💊", value: 2, label: "Active Meds" }
  ],

  appointments: [
    {
      doctor: "Dr. Sarah Johnson",
      specialty: "Primary Care",
      date: "March 12, 2026",
      time: "10:00 AM",
      action: "Review"
    },
    {
      doctor: "Dr. Michael Chen",
      specialty: "Cardiology",
      date: "March 15, 2026",
      time: "2:30 PM",
      action: "Video Visit"
    }
  ]
};

function renderWelcome() {
  document.getElementById("welcomeTitle").textContent =
    `Welcome back, ${overviewData.userName}!`;

  document.getElementById("welcomeSubtitle").textContent =
    overviewData.subtitle;
}

function renderStats() {
  const statsContainer = document.getElementById("statsContainer");
  statsContainer.innerHTML = "";

  overviewData.stats.forEach((stat) => {
    const statCard = document.createElement("div");
    statCard.className = "stat-card";

    statCard.innerHTML = `
      <div class="stat-icon">${stat.icon}</div>
      <div class="stat-info">
        <h3>${stat.value}</h3>
        <p>${stat.label}</p>
      </div>
    `;

    statsContainer.appendChild(statCard);
  });
}

function renderAppointments() {
  const appointmentsContainer = document.getElementById("appointmentsContainer");
  appointmentsContainer.innerHTML = "";

  overviewData.appointments.forEach((appointment) => {
    const appointmentItem = document.createElement("div");
    appointmentItem.className = "appointment-item";

    appointmentItem.innerHTML = `
      <div class="appointment-icon"></div>
      <div>
        <div class="appointment-doctor">${appointment.doctor}</div>
        <div class="appointment-specialty">${appointment.specialty}</div>
      </div>
      <div class="appointment-datetime">
        <div class="appointment-date">${appointment.date}</div>
        <div class="appointment-time">${appointment.time}</div>
      </div>
      <button class="visit-link">${appointment.action}</button>
    `;

    appointmentsContainer.appendChild(appointmentItem);
  });
}

renderWelcome();
renderStats();
renderAppointments();