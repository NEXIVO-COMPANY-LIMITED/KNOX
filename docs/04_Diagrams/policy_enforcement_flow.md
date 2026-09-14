# Runtime Policy Enforcement Workflow
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJyV1G1P4kAQB/CvMqnReAmEB4One8ldUDSYI8RYL+RSfTHsTmHDdre3nYKc8btf+gBynMZcX5B2uvNj5w/tcyCdokBAEBu3knP0DPeDBwsAcHgIIa+NtrPqWhrMsgHFwF7PZuQh1saIA+rEvZgaGXu3IHHQ7vQ+n0/ry+ZKK56Lbvr0Zc+Qc5KLWojj+ITaW4FOe512+0MBJWtnN0QnPqPzLdGZ9qj7MUFWjZ2i7TZoSq+DyNPuWffsLaNSQkbPx1E/TeHGatZo9G9Sj5+EEJuAms2vcFkM2pesl/R8k8F3655gpCXZjKAqf3sRQpR5bOidHmg24SEYu4eg1O7oV04Zl/ewmD+6r7+riGOJTLX9KISoEnqH/ElZbV4Ty/mtM1quo5FDBeW5pgxi7xIYXLQuUC7Iql2zUndaS6qfpmYdksy95nURjVnDHWXsddlWrdzf2ptIP02zGig+tcT/BMbEK+cXtXGtPa3QmH+Bisjy6cxjOocrGzsvKSHLEA3RqxV6ghEtyezee6zaiuOvmatsf4QXDbjEhDw2IJSeyF5iyrmnOvLhpBO1tj0jJxfKrWxrXy1CqMRQm2JHNzZjNAaO4BblAmcEk7lmMjrjrdyNWqGLudr3e/K4SqfCBy5BbeFaGyav7QyOoH873oInUWuzfN8jqzYJDied+h+Kan0cDWipJUE5Y/1U7P5kw0n3dfWmdPJaChoQJOQT1CoQ8BzwnJLyNaUoxtxw8NKAIE8VMl0pzc4HAmI0GTUgwJxduLYyEMA+LyrVwoHGmcekLr/8Afx1p9I==)

This logic flow illustrates how the Knox Policy Engine determines which enterprise restrictions to apply during app startup or backend synchronization.

```mermaid
flowchart TD
    %% Styling
    classDef trigger fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef check fill:#fff3e0,stroke:#e65100,stroke-width:2px;
    classDef action fill:#f1f8e9,stroke:#1b5e20,stroke-width:2px;
    classDef endNode fill:#ffebee,stroke:#c62828,stroke-width:2px;

    Start([App Initialized]):::trigger --> CheckActive{Is Knox License Active?}:::check
    
    CheckActive -- "No" --> RequestActivation[Trigger activateLicense]:::action
    CheckActive -- "Yes" --> FetchPolicy[Load Policies from DB/Backend]:::action

    FetchPolicy --> ApplySecurity[Apply RestrictionPolicy]:::action
    FetchPolicy --> ApplyApps[Apply ApplicationPolicy]:::action
    FetchPolicy --> ApplyNetwork[Apply FirewallPolicy]:::action

    subgraph Enforcement [Hardware Level Enforcement]
        ApplySecurity -- "USB, Camera, ScreenCapture" --> HW1[/Security Lockdown/]
        ApplyApps -- "Silent Install & Package Whitelist" --> HW2[/Software Lockdown/]
        ApplyNetwork -- "Domain Filtering & APN" --> HW3[/Network Lockdown/]
    end

    HW1 --> Ready([Device Secured]):::action
    HW2 --> Ready
    HW3 --> Ready
```

---
[Back to Master Dashboard](Master_Dashboard.md) | [Back to Main Documentation](../README.md)
