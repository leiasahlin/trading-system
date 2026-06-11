# Trading system
A semi-automated stock monitoring and portfolio
management system built around the Qullamaggie
momentum trading strategy.

## Overview
The system scans US equites (NYSE/Nasdaq) for 
high-probability momentum setups based on Kristjan
Kullamägi's three core strategies: breakouts,
episodic pivots and parabolic reversals. It 
supports buy/sell decision-making without 
automated execution.

## Features (in progress)
- [ ] Stock scanner with momentum indicators
  (MA; ADR; Volume) 
- [ ] Position sizing calculator based on risk 
% and ADR
- [ ] Alert engine for breakout and stop-loss
signals
- [ ] Portfolio tracking with P&L and alpha vs index
- [ ] Sell logic with trailing MA and partial exits

## Tech stack
- **Backend:** Java 21, Spring Boot 3, Spring Data JPA
- **Database:** PostgreSQL
- **Frontend:** React, Chart.js (planned)
- **Data:** Yahoo Finance API, IBKR, Börsdata

## Getting started
### Prerequisites
- Java 21
- PostgreSQL 15+
- Maven 3.9+

### Setup
```bash
git clone https://github.com/USERNAME/trading-system.git
cd trading-system
```

```bash
git clone https://github.com/DITT_ANVÄNDARNAMN/trading-system.git
cd trading-system
```

Create the database:
```sql
CREATE DATABASE trading_system;
```

Configure `src/main/resources/application.properties` with your
database credentials, then run:

```bash
mvn spring-boot:run
```

## Project structure
```
src/
├── main/java/com/qullamaggie/tradingsystem/
│   ├── scanner/        # Stock scanning logic
│   ├── indicators/     # Technical indicator calculations
│   ├── portfolio/      # Position and P&L management
│   ├── alerts/         # Alert engine
│   └── data/           # API integrations
└── test/               # JUnit 5 test suites
```
## Status

Under active development. See [issues](../../issues)
for roadmap.