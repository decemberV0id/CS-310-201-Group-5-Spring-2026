const recordsData = {

  userName: "John",

  subtitle: "View your health records",



  chartNo: 1,

  patientNo: 1,

  bloodPressure: "100/70",

  weight: 165,

  temp: 98.8,

  balance: "Satisfactory",

  prescriptions: [Atorvostatin, Metformin],

  tests: "See Profile",

  notes: "continue to monitor cholesterol"



};



function renderRecords(){

    document.getElementById("chart").textContent = 'Chart No: ${recordsData.chartNo}';

    document.getElementById("pID").textContent = "Patient ID: ${recordsData.patientNo}";

    document.getElementById("bp").textContent = "Blood Pressure: ${recordsData.bloodPressure}";

    document.getElementById("wt").textContent = "Weight: ${recordsData.weight}";

    document.getElementById("temp").textContent = "Temperature(F): ${recordsData.temp}";

    document.getElementById("balance").textContent = "Balance: ${recordsData.balance}";

    document.getElementById("rx").textContent = "Prescriptions: ${recordsData.prescriptions}";

    document.getElementById("results").textContent = "Test Results: ${recordsData.tests}";

    document.getElementById("notes").textContent = "Notes: ${recordsData.notes}";

}



renderRecords();