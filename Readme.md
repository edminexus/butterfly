# Butterfly

Butterfly is a **survival-only Elytra-based flight plugin** for Paper / Purpur Minecraft servers.

It allows players to temporarily use **creative-style flight in Survival mode** while wearing an Elytra, while enforcing **balanced trade-offs and clean state handling** to prevent abuse or permanent advantages.

Butterfly relies on **vanilla mechanics**, extending creative flight rather than replacing it with custom physics. This makes it predictable, lightweight, and safe to run long-term.

---

## Core Concept

- Flight is available **only in Survival mode**
- A **usable Elytra** is required at all times
- Flight is automatically disabled when:
  - The Elytra is removed or breaks
  - The player disconnects
  - The server shuts down

---

## Survival Trade-offs

While flight is active, Butterfly applies configurable penalties:

- Increased Elytra durability usage
- Hunger consumption on activation
- Reduced flight speed
- Tracked total flight time per player
- Visual feedback via ActionBar

These mechanics ensure flight remains a **temporary utility**, not a permanent bypass.

---

## Features

- Creative-style flight in Survival
- Elytra-based activation
- Configurable durability, hunger, speed penalties, and fall damage toggle
- Per-player flight lifespan tracking
- ActionBar flight indicator
- Lightweight and performance-friendly

---

## 🧩 Requirements

- **Paper or Purpur** `1.21+`
- **Java 21**
- An **Elytra**

---

## ⌨ Commands

### Player Commands

- ` /butterfly ` — Show plugin information  
- ` /butterfly glue ` — Enable flight  
- ` /butterfly cut ` — Disable flight  
- ` /butterfly toggle ` — Exactly what it's sounds like 
- ` /butterfly lifespan ` — Show your total flight time  
- ` /butterfly canfly ` — Check whether you can fly  
- ` /butterfly help ` — Show all available commands  

### Admin Commands

- ` /butterfly lifespan all ` — Show all players' flight time  
- ` /butterfly debug on ` — Enable debug logging  
- ` /butterfly debug off ` — Disable debug logging  
- ` /butterfly debug toggle ` — ...  
- ` /butterfly reload ` — Reload the configuration  

---

## 🔐 Permissions

### `butterfly.admin`
- **Default:** op  
- Allows administrative commands and debugging tools

---

## 📦 Installation

1. Download the plugin JAR  
2. Place it in the `/plugins` directory  
3. Start or restart the server  
4. Adjust `config.yml` if needed  
