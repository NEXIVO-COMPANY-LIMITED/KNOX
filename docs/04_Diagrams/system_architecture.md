# System Architecture Diagram
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJydVctu2zAQ/JWFggQp6jzaoIeqQAHHcpogdq1GLnpQcliJa5s1RQokldht+u8F9XD8kFOgPBjEcna4s+OlfnupYuT54E015jMYB/cSAODwEL4IlaCAyC4FmSqaCjQmoAmkQhUMJlwI/2DycdIxVqs5+QcXFxf1/uSJMzvz3+eLTqqE0v7B+fn5py0azPOaJEn+m8SweU3CJmw/ybt80QQYmhlqjUsfPsCHV6iVqZmJ6HXm/Rwsafr0WnU7EisWUySVLzdyotFYXaS20ATxvddTOlcaLcElpnOSDI6g52y59x6qZLcCNLNEoWbxD0qgyzIuX2IPvu+XTr7gu+FNfNePxm4DZ/BVMTr9aVqAwWV8HH0bcEsQoMUEDb1xMJa0XA4nJ58d48Y9ZTC4rGIk2Y7mgB55Wmodo56ShQgzU8hpc3B8K9UCLk4Xb99saF4RdPN8gEvSjiLEpYIg7LngBtqt7zfxELnsppY/cruEM/h+48Rgnm8C7ygl/kg6rjp5BJdK2VW0NSUi7aqNq6KHSnKrdB1sTegpISi1SsdjEpSR1UvoyymX7XDXhOFUx2UzQiV4ugPfFlt2vs5rLbYErOrYU147y5qRG144YBTcOisaG8uKXay/sCQNV9LsONMfDOO+tKRzzQ3BgKckDcEQJU6rhhs23/bIWM1Ty5WM1/Z1b1pTunlencbdPBc8xX8l3HJl5nH568auMFZl/FeZ1oq/4pqeUIi42WyTt3padrg/GO4/XNO3H7RStx9SKtl/3FT9b5u7kmnF2ShyRndHUQhncI2aPaEmKIdxx+IgHNbD0fx919xV9ZenWaMorq+AW9KSBBxBRGmhuV224ddVBOFWJ4NwWB6Moi1hWwoPD6GnsgyuhHqq+dem4PTk8/P1eBxCOIrGsJrZ55cHr3zsHOyOMmWpJEPJzHMzb14HvIx0hpx5Pvz27Iyy8qPMaIKFsN6fDnhFztBSn7n3w/NhgsJQBzwsrIqWMvV8sLpwkQoYcJxqzOrwn7/f92dV)

This diagram analyzes the structural relationships between the Android DPC App, the Samsung Knox SDK layer, and the remote management backend.

```mermaid
graph TD
    %% Global Styles
    classDef cloud fill:#f9f,stroke:#333,stroke-width:2px,color:#000;
    classDef app fill:#bbf,stroke:#333,stroke-width:2px,color:#000;
    classDef sdk fill:#dfd,stroke:#333,stroke-width:1px,stroke-dasharray: 5 5,color:#000;
    classDef os fill:#eee,stroke:#333,stroke-width:1px,color:#000;
    classDef db fill:#ffd,stroke:#333,stroke-width:2px,color:#000;

    subgraph Infrastructure ["Corporate Backend & Cloud"]
        Dashboard[Web Admin Dashboard]:::cloud
        API[REST API / Node.js]:::cloud
        DB[(SQLite Database)]:::db
        Dashboard --> API
        API --> DB
    end

    subgraph Device ["Target Samsung Device (Knox 3.x+)"]
        subgraph AppLayer ["Payo DPC App"]
            UI[MainActivity / UI]:::app
            Receiver[Admin & Boot Receiver]:::app
            Service[DeviceMonitorService]:::app
            Collector[Telemetry Engine]:::app
            KnoxMgr[Knox Policy Engine]:::app
            
            UI --> KnoxMgr
            Service --> Collector
            Collector --> KnoxMgr
        end

        subgraph KnoxSDK ["Samsung Knox SDK Extensions"]
            ELM[Enterprise License Manager]:::sdk
            Restriction[Restriction Policy]:::sdk
            AppPolicy[Application Policy]:::sdk
            Kiosk[Kiosk & Customization]:::sdk
            Firewall[Firewall Policy]:::sdk
            
            KnoxMgr --> ELM
            KnoxMgr --> Restriction
            KnoxMgr --> AppPolicy
            KnoxMgr --> Kiosk
            KnoxMgr --> Firewall
        end

        subgraph AndroidOS ["AOSP / Hardware Layer"]
            DPM[Device Policy Manager]:::os
            OS[Android Kernel & Security]:::os
            KnoxMgr --> DPM
            DPM --> OS
        end
    end

    %% Comm Flows
    Collector -.->|HTTP POST Telemetry| API
    API -.->|Remote Commands| Service
```

---
[Back to Master Dashboard](Master_Dashboard.md) | [Back to Main Documentation](../README.md)
