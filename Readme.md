# Butterfly

A survival-only Elytra-based flight plugin for Paper / Purpur Minecraft servers.

Butterfly allows players to temporarily use creative-fly in Survival mode using Elytra,
with durability cost, hunger cost, and clear visual feedback.


## Features

- Creative-like fly in Survival
- Elytra-based flying (no Elytra = no flight)
- Increased Elytra durability drain while flying
- Lifespan tracking per player
- Clean ActionBar flight indicator
- Designed for Paper / Purpur 1.21+


## Requirements

- Java **21**
- Paper or Purpur **1.21+**
- and Elytra


## 📜 Commands

- `/butterfly` -> Shows Plugin info
- `/butterfly glue` -> Enables flying
- `/butterfly cut` -> Disables flying
- `/butterfly toggle` -> Exactly what it sounds like
- `/butterfly lifespan` -> Shows your total flight time
- `/butterfly lifespan all` -> Shows all players' flight time (admin)


## Permissions

- `butterfly.admin` | Allows `/butterfly lifespan all`


## ⚙️ How It Works (Short)

- Flight only works in **Survival mode**
- Player must be wearing an **usable Elytra**
- While flying:
  - Elytra durability drains faster
  - Hunger drains by half on initial gluing
  - Flight time is tracked
- Removing an Elytra disables flight automatically (Same happens when it breaks)


## 🛠 Installation

1. Download the latest release
2. Place `Butterfly.jar` into your server’s `/plugins` folder
3. Restart the server
4. Use `/butterfly` in-game to verify installation


## 📄 License

This project is licensed under the MIT License.
