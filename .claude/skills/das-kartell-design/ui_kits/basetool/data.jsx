/* Profit Basetool — sample data for the kit (fictional, on-theme). */

const NEXT_MISSION = {
  id: 1, name: "Operation Tiefschlag", status: "PLANNED",
  description: "Coordinated quantanium run through Pyro — combat escort + refinery handover.",
  meetingTime: "29.05.2026, 18:45", startTime: "29.05.2026, 19:00",
  participants: "12/18",
};

const MISSIONS = [
  { id: 1, name: "Operation Tiefschlag", status: "PLANNED", start: "29.05.2026, 19:00", owner: "Valk", dept: "raumueberlegenheit", deptLabel: "Raumüberlegenheit", participants: "12/18" },
  { id: 2, name: "Aaron Halo Sweep", status: "ACTIVE", start: "28.05.2026, 20:30", owner: "Mara", dept: "profit", deptLabel: "Profit", participants: "6/8" },
  { id: 3, name: "Pyro Recon — Ghost Hollow", status: "PLANNED", start: "31.05.2026, 21:00", owner: "Hex", dept: "sub-radar", deptLabel: "Sub-Radar", participants: "3/6" },
  { id: 4, name: "Daymar Salvage Pull", status: "COMPLETED", start: "24.05.2026, 19:00", owner: "Dane", dept: "profit", deptLabel: "Profit", participants: "9/9" },
  { id: 5, name: "Refinery Convoy — ARC-L1", status: "COMPLETED", start: "22.05.2026, 18:00", owner: "Mara", dept: "search-rescue", deptLabel: "Search & Rescue", participants: "7/7" },
  { id: 6, name: "Checkmate Drill", status: "CANCELLED", start: "20.05.2026, 20:00", owner: "Valk", dept: "marinekorps", deptLabel: "Marinekorps", participants: "0/10" },
];

const SHIPS = [
  { id: 1, name: "Schwarze Witwe", type: "Constellation Andromeda", maker: "RSI", owner: "Valk", insurance: "LTI", location: "Area18 — ArcCorp", fitted: true },
  { id: 2, name: "Erntemaschine", type: "MOLE", maker: "Argo", owner: "Mara", insurance: "6 Months", location: "Lorville — Hurston", fitted: true },
  { id: 3, name: "Nadelöhr", type: "Vulture", maker: "Drake", owner: "Dane", insurance: "LTI", location: "GrimHEX — Yela", fitted: false },
  { id: 4, name: "Stiller Bote", type: "Hull C", maker: "MISC", owner: "Hex", insurance: "12 Months", location: "Everus Harbor", fitted: false },
  { id: 5, name: "Eisenfaust", type: "Hammerhead", maker: "Aegis", owner: "Valk", insurance: "LTI", location: "Seraphim Station", fitted: true },
  { id: 6, name: "Spürhund", type: "Terrapin", maker: "Anvil", owner: "Hex", insurance: "24 Months", location: "Area18 — ArcCorp", fitted: true },
];

const TERMINALS = [
  { name: "TDD Area18", planet: "ArcCorp" },
  { name: "Baijini Point", planet: "ArcCorp" },
  { name: "CRU-L1", planet: "Crusader" },
  { name: "Lorville TDD", planet: "Hurston" },
];

const MATERIALS = [
  { kind: "Metals", rows: [
    { name: "Laranite", prices: [3090, 2980, 3010, 2760] },
    { name: "Agricium", prices: [2810, 2700, 2560, 2640] },
    { name: "Titanium", prices: [null, 980, 920, 940] },
  ]},
  { kind: "Gasses", rows: [
    { name: "Hydrogen", prices: [120, 110, null, 118] },
    { name: "Chlorine", prices: [1580, 1490, 1520, null] },
  ]},
  { kind: "High Value", rows: [
    { name: "Quantanium", prices: [29400, null, 28800, 27200], volatile: true },
    { name: "Bexalite", prices: [42100, 41800, null, 40900] },
  ]},
];

Object.assign(window, { NEXT_MISSION, MISSIONS, SHIPS, TERMINALS, MATERIALS });
