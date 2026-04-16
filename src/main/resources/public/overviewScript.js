const username = "johnm";

let currentOverviewData = {
  appointments: []
};

const fallbackOverviewData = {
  userName: username,
  upcomingCount: 2,
  unreadCount: 1,
  activeMedsCount: 2,
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

async function fetchOverviewData() {
  try {
    const response = await fetch("/overview");

    if (response.ok) {
      const data = await response.json();
      return normalizeOverviewData(data);
    }
  } catch (error) {
    console.error("Failed route: /overview", error);
  }

  return fallbackOverviewData;
}

function normalizeOverviewData(data) {
  return {
    userName:
      data.userName ||
      data.username ||
      data.currentUser ||
      username,

    upcomingCount:
      data.upcomingCount ??
      data.upcoming ??
      data.appointmentCount ??
      (Array.isArray(data.appointments) ? data.appointments.length : 0),

    unreadCount:
      data.unreadCount ??
      data.unreadMessages ??
      data.messagesUnread ??
      0,

    activeMedsCount:
      data.activeMedsCount ??
      data.medicationCount ??
      data.activeMedications ??
      0,

    appointments: Array.isArray(data.appointments)
      ? data.appointments.map((appt) => ({
          doctor: appt.doctor || appt.provider || "Unknown Doctor",
          specialty: appt.specialty || appt.type || "",
          date: appt.date || "",
          time: appt.time || "",
          action: appt.action || "View"
        }))
      : []
  };
}

async function loadOverview() {
  const data = await fetchOverviewData();
  currentOverviewData = data;

  renderWelcome(data);
  renderStats(data);
  renderAppointments(data);
}

function renderWelcome(data) {
  const welcomeTitle = document.getElementById("welcomeTitle");
  const welcomeSubtitle = document.getElementById("welcomeSubtitle");

  if (welcomeTitle) {
    welcomeTitle.textContent = `Welcome back, ${data.userName}!`;
  }

  if (welcomeSubtitle) {
    welcomeSubtitle.textContent = "Here is your health overview for today";
  }
}

function renderStats(data) {
  const statsContainer = document.getElementById("statsContainer");
  if (!statsContainer) return;

  statsContainer.innerHTML = "";

  const stats = [
    { icon: "📅", value: data.upcomingCount, label: "Upcoming" },
    { icon: "💬", value: data.unreadCount, label: "Unread" },
    { icon: "💊", value: data.activeMedsCount, label: "Active Meds" }
  ];

  stats.forEach((stat) => {
    const card = document.createElement("div");
    card.className = "stat-card";

    card.innerHTML = `
      <div class="stat-icon">${stat.icon}</div>
      <div class="stat-info">
        <h3>${stat.value}</h3>
        <p>${stat.label}</p>
      </div>
    `;

    statsContainer.appendChild(card);
  });
}

function renderAppointments(data) {
  const container = document.getElementById("appointmentsContainer");
  if (!container) return;

  container.innerHTML = "";

  if (!data.appointments.length) {
    container.innerHTML = `<p class="empty-message">No upcoming appointments.</p>`;
    return;
  }

  data.appointments.forEach((appt, index) => {
    const item = document.createElement("div");
    item.className = "appointment-item";

    item.innerHTML = `
      <div class="appointment-icon"></div>
      <div>
        <div class="appointment-doctor">${appt.doctor}</div>
        <div class="appointment-specialty">${appt.specialty}</div>
      </div>
      <div class="appointment-datetime">
        <div class="appointment-date">${appt.date}</div>
        <div class="appointment-time">${appt.time}</div>
      </div>
      <button class="visit-link" data-index="${index}">${appt.action}</button>
    `;

    container.appendChild(item);
  });
}

function showScheduleModal() {
  removeExistingModal();

  const modalOverlay = document.createElement("div");
  modalOverlay.id = "customModalOverlay";
  modalOverlay.style.position = "fixed";
  modalOverlay.style.top = "0";
  modalOverlay.style.left = "0";
  modalOverlay.style.width = "100%";
  modalOverlay.style.height = "100%";
  modalOverlay.style.background = "rgba(0, 0, 0, 0.45)";
  modalOverlay.style.display = "flex";
  modalOverlay.style.alignItems = "center";
  modalOverlay.style.justifyContent = "center";
  modalOverlay.style.zIndex = "9999";

  const modalBox = document.createElement("div");
  modalBox.style.background = "#ffffff";
  modalBox.style.width = "360px";
  modalBox.style.maxWidth = "90%";
  modalBox.style.padding = "20px";
  modalBox.style.borderRadius = "14px";
  modalBox.style.boxShadow = "0 10px 30px rgba(0,0,0,0.2)";
  modalBox.style.fontFamily = "Arial, sans-serif";

  modalBox.innerHTML = `
    <h3 style="margin-top:0; margin-bottom:14px;">Schedule New Appointment</h3>
    <label style="display:block; margin-bottom:6px;">Doctor Name</label>
    <input id="apptDoctor" type="text" placeholder="Enter doctor name"
      style="width:100%; padding:10px; margin-bottom:12px; border:1px solid #ccc; border-radius:8px; box-sizing:border-box;" />

    <label style="display:block; margin-bottom:6px;">Date</label>
    <input id="apptDate" type="date"
      style="width:100%; padding:10px; margin-bottom:12px; border:1px solid #ccc; border-radius:8px; box-sizing:border-box;" />

    <label style="display:block; margin-bottom:6px;">Time</label>
    <input id="apptTime" type="time"
      style="width:100%; padding:10px; margin-bottom:16px; border:1px solid #ccc; border-radius:8px; box-sizing:border-box;" />

    <div style="display:flex; gap:10px; justify-content:flex-end;">
      <button id="cancelScheduleBtn"
        style="padding:10px 14px; border:none; border-radius:8px; cursor:pointer;">Cancel</button>
      <button id="saveScheduleBtn"
        style="padding:10px 14px; border:none; border-radius:8px; background:#51d88a; color:white; cursor:pointer;">Save</button>
    </div>
  `;

  modalOverlay.appendChild(modalBox);
  document.body.appendChild(modalOverlay);

  document.getElementById("cancelScheduleBtn").addEventListener("click", removeExistingModal);

  document.getElementById("saveScheduleBtn").addEventListener("click", () => {
    const doctor = document.getElementById("apptDoctor").value.trim();
    const date = document.getElementById("apptDate").value;
    const time = document.getElementById("apptTime").value;

    if (!doctor || !date || !time) {
      alert("Please fill in all appointment fields.");
      return;
    }

    alert(`Appointment scheduled with ${doctor} on ${date} at ${time}.`);
    removeExistingModal();
  });

  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) {
      removeExistingModal();
    }
  });
}

function showAppointmentActionModal(appointment) {
  removeExistingModal();

  const modalOverlay = document.createElement("div");
  modalOverlay.id = "customModalOverlay";
  modalOverlay.style.position = "fixed";
  modalOverlay.style.top = "0";
  modalOverlay.style.left = "0";
  modalOverlay.style.width = "100%";
  modalOverlay.style.height = "100%";
  modalOverlay.style.background = "rgba(0, 0, 0, 0.45)";
  modalOverlay.style.display = "flex";
  modalOverlay.style.alignItems = "center";
  modalOverlay.style.justifyContent = "center";
  modalOverlay.style.zIndex = "9999";

  const modalBox = document.createElement("div");
  modalBox.style.background = "#ffffff";
  modalBox.style.width = "380px";
  modalBox.style.maxWidth = "90%";
  modalBox.style.padding = "20px";
  modalBox.style.borderRadius = "14px";
  modalBox.style.boxShadow = "0 10px 30px rgba(0,0,0,0.2)";
  modalBox.style.fontFamily = "Arial, sans-serif";

  const isVideoVisit = (appointment.action || "").toLowerCase().includes("video");
  const title = isVideoVisit ? "Video Visit Details" : "Appointment Review";

  modalBox.innerHTML = `
    <h3 style="margin-top:0; margin-bottom:14px;">${title}</h3>
    <p style="margin:8px 0;"><strong>Doctor:</strong> ${appointment.doctor || "Unknown Doctor"}</p>
    <p style="margin:8px 0;"><strong>Specialty:</strong> ${appointment.specialty || "N/A"}</p>
    <p style="margin:8px 0;"><strong>Date:</strong> ${appointment.date || "N/A"}</p>
    <p style="margin:8px 0;"><strong>Time:</strong> ${appointment.time || "N/A"}</p>
    <p style="margin:8px 0;"><strong>Visit Type:</strong> ${appointment.action || "View"}</p>

    <div style="display:flex; justify-content:flex-end; margin-top:18px;">
      <button id="closeActionBtn"
        style="padding:10px 14px; border:none; border-radius:8px; background:#51d88a; color:white; cursor:pointer;">
        OK
      </button>
    </div>
  `;

  modalOverlay.appendChild(modalBox);
  document.body.appendChild(modalOverlay);

  document.getElementById("closeActionBtn").addEventListener("click", removeExistingModal);

  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) {
      removeExistingModal();
    }
  });
}

function removeExistingModal() {
  const oldModal = document.getElementById("customModalOverlay");
  if (oldModal) {
    oldModal.remove();
  }
}

function setupScheduleButton() {
  const scheduleBtn = document.querySelector(".schedule-btn");

  if (scheduleBtn) {
    scheduleBtn.addEventListener("click", showScheduleModal);
  }
}

function setupVisitButtons() {
  document.addEventListener("click", (event) => {
    if (event.target.classList.contains("visit-link")) {
      const index = Number(event.target.dataset.index);

      if (!Number.isNaN(index) && currentOverviewData.appointments[index]) {
        showAppointmentActionModal(currentOverviewData.appointments[index]);
      }
    }
  });
}

loadOverview();
setupScheduleButton();
setupVisitButtons();