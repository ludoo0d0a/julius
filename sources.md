# Data Sources and Fuel Price Coverage

Julius aggregates data from a wide variety of official and community sources to provide the most accurate and up-to-date information on fuel prices and EV charging stations across the globe.

---

## 🚀 Auto Mode

The application features an **Auto Mode** that intelligently manages data sources to provide a seamless experience without manual configuration.

- **Location-Aware:** Julius automatically identifies the country you are in using your current coordinates. It maintains a **50km cross-border tolerance** to ensure you see nearby stations even when close to a frontier.
- **Zoom Logic:** To optimize performance and data usage, POI loading is bypassed when the map is zoomed out (zoom level < 11).
- **Intelligent Selection:** "Eligible" providers (official government sources) are prioritized and automatically enabled. Some sources (like Routex or Etalab) are excluded from auto-selection to avoid clutter or redundant data unless manually picked by the user.

---

## ⚡ EV Charging & OCPI

To provide real-time availability and accurate technical data for EV charging stations, Julius leverages the **OCPI (Open Charge Point Interface)** protocol.

- **OCPI (Open Charge Point Interface):** An open-source protocol that allows for automated roaming between EV charging networks. It enables Julius to receive real-time updates on charger status (Available, Occupied, Out of Order), technical specifications (connector types, power in kW), and dynamic pricing.
- **Eco-Movement:** A premier global aggregator of EV charging station data. Eco-Movement sources data directly from hundreds of Charge Point Operators (CPOs) worldwide, validating and normalizing it to ensure the highest data quality. In Julius, Eco-Movement acts as a comprehensive global source for IRVE (Infrastructures de Recharge pour Véhicules Électriques) data via its OCPI-compliant API.

---

## 🌍 Global & Multi-Country Providers

These providers cover multiple regions or provide fallback data when specific local APIs are unavailable.

| Provider | Format | Access | Source Type | Scope | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **OpenStreetMap** | JSON (Overpass) | Free | Community | Global | No | Real-time (Community) |
| **OpenVanCamp** | JSON | Free | Community / Aggregator | Europe | No | Weekly Reference Prices |
| **OpenChargeMap** | JSON | API Key | Community | Global | No | Real-time (Community) |
| **Eco-Movement** | JSON (OCPI) | API Key | Private | Global | **Yes** | Real-time |
| **Routex** | JSON | Free | Multinational | Europe | No | Daily |
| **Fuelo.net** | HTML (Scraped) | Free | Private | Balkans / Central Europe | No | Daily |
| **DrivstoffAppen** | JSON | Free | Private | Nordics | No | ~1 hour cache |
| **Ionity / Fastned** | JSON (OCPI) | Free | Private | Europe | **Yes** | Real-time |

---

## 📍 Country-Specific Coverage

### 🇦🇷 Argentina
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Secretaría de Energía** | CSV | Free | Government | No | No | Daily |

### 🇦🇺 Australia
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NSW FuelCheck** | JSON | API Key | Government | No | No | Real-time |
| **FuelWatch** | XML/RSS | Free | Government | No | No | Daily |

### 🇦🇹 Austria
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **E-Control** | JSON | Free | Government | No | No | Real-time |

### 🇧🇪 Belgium
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Official Scraper** | HTML | Free | Government | No | No | Daily (Max Prices) |
| **STIB/MIVB** | JSON | Free | Government | No | No | Real-time (Transit) |

### 🇭🇷 Croatia
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **MZOE** | JSON | Free | Government | No | No | Daily |

### 🇩🇰 Denmark
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Fuelprices.dk** | JSON | API Key | Private | No | No | ~1 hour cache |
| **DrivstoffAppen** | JSON | Free | Private | Yes | No | ~1 hour cache |

### 🇫🇮 Finland
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Polttoaine.net** | HTML | Free | Private | No | No | Daily |
| **DrivstoffAppen** | JSON | Free | Private | Yes | No | ~1 hour cache |

### 🇫🇷 France
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DataGouv (Flux Instantané)** | JSON | Free | Government | No | No | 10 minutes |
| **GasAPI** | JSON | Free | Community (Mirror) | No | No | 10 minutes |
| **DataGouv IRVE** | JSON | Free | Government | No | No | Daily |
| **Belib' (Paris)** | JSON | Free | Government | No | No | Real-time (Availability) |

### 🇩🇪 Germany
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tankerkönig (MTS-K)** | JSON | API Key | Government | No | No | Real-time |

### 🇬🇷 Greece
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FuelGR** | JSON | Free | Private | No | No | Daily |

### 🇮🇪 Ireland
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Pick A Pump** | JSON | Free | Private | No | No | Daily |

### 🇮🇹 Italy
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **MIMIT** | CSV | Free | Government | No | No | Daily |

### 🇱🇺 Luxembourg
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Chargy** | KML/JSON | Free | Government | No | No | Real-time |
| **Mobiliteit.lu** | JSON | API Key | Government | No | No | Real-time (Transit) |
| **ANWB / OpenVanCamp** | JSON | Free | Private/Comm | Yes | No | Weekly fallback |

### 🇲🇽 Mexico
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CRE** | XML | Free | Government | No | No | Daily |

### 🇲🇩 Moldova
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ANRE** | JSON | Free | Government | No | No | Daily |

### 🇳🇱 Netherlands
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ANWB** | JSON | Free | Private | No | No | ~1 hour cache |

### 🇳🇴 Norway
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DrivstoffAppen** | JSON | Free | Private | Yes | No | ~1 hour cache |

### 🇵🇹 Portugal
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DGEG (Mainland)** | JSON | Free | Government | No | No | ~1 hour cache |
| **Madeira Official** | HTML | Free | Government | No | No | 24 hour cache |

### 🇷🇴 Romania
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Peco Online** | JSON | Free | Private | No | No | Daily |

### 🇷🇸 Serbia
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NIS / Cenagoriva** | JSON/HTML | Free | Private | No | No | Daily |

### 🇸🇮 Slovenia
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Goriva.si** | JSON | Free | Government | No | No | Daily |

### 🇪🇸 Spain
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Minetur** | JSON | Free | Government | No | No | ~1 hour cache |

### 🇸🇪 Sweden
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DrivstoffAppen** | JSON | Free | Private | Yes | No | ~1 hour cache |

### 🇬🇧 United Kingdom
| Provider | Format | Access | Source Type | Multinational | OCPI | Update Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CMA Open Data** | JSON | Free | Government | No | No | ~1 hour cache |

---

## 💡 Notes on Methodology

- **Official vs Scraped:** Official APIs are always preferred for reliability. When unavailable, Julius uses high-quality scrapers or community-driven data (OSM enrichment).
- **Fallbacks:** For countries not listed above, Julius relies on **OpenStreetMap** for station locations and **OpenVanCamp** for national reference price averages.
- **Multinational Providers:** Sources like **Fuelo** and **DrivstoffAppen** provide coverage for numerous countries in their respective regions using a unified interface.
