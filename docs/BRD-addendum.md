# The Lorax · Current-build BRD addendum

**Baguio Forest Registry | 25 November 2026**

A ranger and an administrator should be able to follow one observation from planting site to approved inventory. The ranger records the location, condition and measurements. The administrator checks the submission. Both can then find the same tree by its ID. This update keeps the JavaFX client, RMI server and local file storage described in the original paper.

## Where this revision fits in the paper

| BRD section | Current-build material |
| --- | --- |
| Problem and objectives | The review and map screens address two documented problems. An unreviewed field report can be mistaken for an official record, and a written description alone makes the tree hard to locate on a return visit. |
| Requirements and scope | The table below extends the November paper’s Chapter 3 matrix on pp. 51–53. The ranger and administrator retain their original responsibilities. **My submissions** makes the ranger’s own review outcomes visible. |
| Design and implementation | The UI adopts the linked forest registry’s sidebar, wording and card layout. The field map uses the recorded coordinates and bundled Baguio tiles. Photos appear when a record includes them. The demonstration starts with four fictional records. |
| Testing and evaluation | Use the current-build evidence below alongside Chapter 5. The earlier paper reports 49 of 55 executed cases passing and 39 of 47 functional requirements fully verified. Those figures belong to the earlier build and have a different denominator from the checks here. |

## From problem to observable result

| Problem and objective | Paper requirements | Implemented module | Evidence for this revision |
| --- | --- | --- | --- |
| P1 · Consolidate the team’s field records and find an individual tree again | FR-B1, B3, B4, B7, B8 | Sapling form, tree service, active inventory | R06–R08 reject invalid input. R12 keeps the assigned ID after approval. R13 finds a species. R18 reads the saved JSON. UI01–UI02 render four cards and find one by ID. |
| P2 · Return to the recorded planting location under intermittent internet access | FR-B5, B13, constraint C1 | Coordinate validation, Field Map and bundled Leaflet tiles | R06 rejects malformed coordinates. UI05 opens the offline map and counts four markers. UI06 filters them to one at-risk tree, and UI09 checks that the bundled raster imagery loads. Field coordinate accuracy still requires a site check. |
| P3 · Separate submitted observations from the approved inventory | FR-C1, C3, C4, C5, C6 | Pending Review, My submissions and tree service | R09–R12 follow a ranger submission through approval. R14–R15 cover approval and rejection visibility. R21 checks that a ranger cannot approve. UI08 opens the selected entry on the administrator’s screen. |
| P4 · Attribute actions to an account and make decisions reviewable | FR-A1–A3, A8, D1, D2 and NFR-07, 08, 10 | User service, session manager, account store, activity log | R01–R05 cover setup, registration, duplicate detection, sign-in and stored password format. R17 finds the approval event, R20 checks sign-out and R22 counts successful submissions in the audit after rejected input. |
| P5 · Keep a tree’s supporting photograph with the approved record | FR-B6 | Pending image storage, approved image storage and review | R23 sends a generated PNG through approval and compares the returned bytes and stored file. The presentation rehearsal should also open a real, permitted field photo on the second laptop. |
| P6 · Coordinate follow-up work | FR-E1, E3 | Messages, chat service | R16 sends a message and reads the conversation as its recipient. UI03 opens the messaging workspace. |
| Retain removed records and let staff move between workspaces | FR-B11 and inventory navigation | Archive and sidebar | R19 checks that a removed record appears in Archive. UI04 opens Profile. UI07 returns from the map to the four-card inventory. |
| Connect two laptops and recover from host interruption | FR-F1, F2, F5 and NFR-05 | Launcher, server discovery, server shutdown and Reconnect | Two-laptop acceptance remains open. Follow the rehearsal below and record the observed result. |

## Data and decisions

| Item | Where it lives | How it relates to the workflow |
| --- | --- | --- |
| Account | Server account file | The username identifies the owner. The server stores salted password hashes and issues a session token after sign-in. Older password records are migrated when the account store opens. |
| Pending tree | Pending JSON store and optional image | The server assigns the ID and owner from the signed-in session. The ranger can see the pending submission. It is excluded from the approved inventory. |
| Approved tree | Active JSON store and optional tree image | Approval carries the ID, owner, measurements, planting date, condition and location into the inventory. Search and map pins use the same record. |
| Rejection and activity | Server activity log | The administrator’s decision is recorded. Rejections recorded by this version can be displayed to the submitting ranger. |
| Archived tree | Deleted-tree JSON store | Removing an active record preserves an entry for the administrator to inspect. |
| Session | Server memory | Sign-out ends the session. The host is required for reads, writes and review. The field map’s offline mode supplies local map imagery. |

## Test evidence and reproduction

On 25 November 2026 the Maven compile succeeded, 23 service checks passed and nine JavaFX checks passed.

The Windows build uses JDK 25, JavaFX 23.0.2 and Maven. The service suite creates a temporary data directory. The screen suite opens the actual JavaFX controls with a separate temporary copy of the four demonstration records and a local map configuration. Run the commands from the project root with JDK 25 selected. Stop the demonstration server first because the services use the same ports.

```powershell
.\mvnw.cmd compile
.\verification\run.ps1
.\verification\run.ps1 -Screens
```

The identifiers in the matrix refer to [the service checks](../verification/RegistryWorkflow.java) and [the screen checks](../verification/RegistryScreens.java). The screenshot runner replaces the example PNG files with captures from its own run.

| Screen capture | What the panel can inspect |
| --- | --- |
| [Sign-in](screens/sign-in.png) | The registry branding, account fields and local server indicator |
| [Forest Registry](screens/forest-registry.png) | Four seeded records, status totals and percentages, filters and tree IDs |
| [Field Map](screens/field-map.png) | Baguio map, offline selection and approved records |
| [Pending Review](screens/pending-review.png) | A ranger’s selected submission with its ID, measurements, condition, location and review actions |

## Acceptance work for the presentation laptops

The automated service run calls the implementations directly. The environment denied the loopback socket connection during the transport run. A second laptop, an actual LAN interruption and participant usability observations remain unverified. Record the two device names, build date, dataset, observed outcome and evidence for each rehearsal below before updating the final paper’s pass counts.

| Scenario | Expected evidence |
| --- | --- |
| Start the host and join from a ranger laptop on the same LAN. Try the host address if discovery fails. | The remote ranger signs in. Record which connection method worked. |
| Keep the LAN connected, remove internet access and open Field Map in Offline mode. | The four seeded records have pins and the bundled Baguio area remains readable. Check the intended zoom and site. |
| Submit a field photo and approve the tree on the other laptop. Search for the ID on both. | The same ID, location, status and photo appear on both clients after refresh. |
| Halt the host, observe the client, restart the host and reconnect. | The client indicates loss of service, then signs in again and retrieves the previously approved ID. |
| Ask a classmate to register a tree and another to review it without prompting. | Record completion, any wrong turns and comments with their permission. Quote actual observations in the evaluation. |

## Project sources

*9629 The Lorax final paper*. (n.d.). [Project paper](https://chatgpt.com/library?file_id=libfile_71d91beb4f4c81919fbbac3867ff1f14&account_id=personal)

*Software engineering final project guidelines* (Version 1.4). (n.d.). [Course guidelines](https://chatgpt.com/library?file_id=libfile_e3b47df7e0b48191ad9a8aa95f46ebff&account_id=personal)

*Forest monitoring app* [Interactive prototype]. (n.d.). Figma. [Design reference](https://www.figma.com/make/6xw7n7xK5jDBC4QXwf7d8x/Forest-Monitoring-App)
