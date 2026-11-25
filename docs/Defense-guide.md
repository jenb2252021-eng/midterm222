# Defense guide · The Lorax

**Opening question:** A ranger planted a Benguet pine last month. How can the next ranger find the same tree and know an administrator checked the record?

Use one tree throughout the defense. Let the panel see its assigned ID on the ranger’s submission, the administrator’s review and the final inventory. The project guidelines give the system 50%, documentation 30% and presentation 20%. The demonstration below shows a working feature, the problem it addresses and the evidence for it in the same sequence.

## Three-minute live demonstration

| Time | On screen | Suggested words and handoff |
| --- | --- | --- |
| 0:00–0:25 | Open **Registry** with the four sample trees. | “A species name alone is ambiguous. Two rangers can record two Benguet pines. Each record here has an ID, coordinates and a condition so another ranger can find the same tree.” |
| 0:25–1:05 | On the ranger laptop, choose **Register Sapling**. Enter a species, measurements and planting date, choose a photo and pin the site. Submit and open **My submissions**. | “This is our field observation. I’ll read its assigned ID aloud. It is pending, so the inventory total should still be four.” |
| 1:05–1:45 | On the host, open **Pending Review**. Point to the ID, owner, coordinates and photo. Approve the entry. | “I’m checking whether the photograph and measurements support this record. Approval moves this observation into the inventory with the same ID.” |
| 1:45–2:30 | Return to **Registry**, search for the ID, then open **Field Map** and filter to the tree’s condition. | “The count has risen to five. Searching this ID finds the approved record. A second ranger can use its pin when returning to inspect the site.” |
| 2:30–3:00 | Open **System States** and find the approval entry. End on the requirements matrix. | “The log records the review. Our test checks that approval preserves the ID and photograph. The matrix links that result back to the original problem and requirement.” |

The numbers four and five assume the initial four-record dataset. The demo saves edits. On a later run, read the actual starting count and show a one-record increase. The four seeded trees are fictional. Use a photograph the team has permission to present.

## Before the panel

1. Use **The Lorax · Demonstration** on the host and **The Lorax · Join a server** on the ranger laptop. Both should run the same build on the same LAN.
2. Confirm that a ranger submission reaches the host and that the approved photo opens on the second laptop. Keep its ID for the test evidence.
3. Keep the LAN connected while testing the map without internet. Then stop and restart the host separately, watch for the disconnection indicator, and reconnect. Record how each test behaved.
4. Give the ranger, reviewer and evaluator distinct speaking roles. If one person presents, keep the two role views ready and announce the change of role.
5. Keep the [actual screen captures](screens/) and the [BRD matrix](BRD-addendum.md) available. If the live network fails during the defense, identify the failure and use the captures to explain the workflow. Report live and recorded evidence accurately.

## Likely panel questions

**Why retain a desktop client when the design is in Figma?** The original BRD describes a JavaFX application on the local network. We used the Figma design for navigation and wording while retaining that architecture and its existing data. Both users share one review queue through the server.

**What works without internet?** The desktop client can draw map tiles bundled for the Baguio area. While the local server remains reachable, collaborators can exchange records over the LAN. Outside the bundled map coverage, background detail is limited. Losing the host interrupts registry operations.

**How do you prevent an unreviewed observation from affecting the inventory?** A ranger submission enters the pending store. The server checks the reviewer’s session before approval. Tests R10, R12 and R21 cover visibility, a successful decision and the role restriction.

**What does the password change protect?** The account file stores salted password hashes. The server checks a session token before privileged operations. Java RMI traffic is unencrypted in this build. The design assumes the project’s trusted local network.

**How do you know the project improved?** Show a reproduced result. For example, R23 compares the submitted PNG with the approved photo, while UI05 finds all four seeded markers on the offline map. Use the previous paper’s evaluation as the historical baseline and the dated addendum for this build. A claim about ranger satisfaction needs observations from actual users.

**What evidence was outside the automated run?** The automated suites exercised individual services and JavaFX screens. The two-laptop discovery, disconnect and reconnect sequence, live field location accuracy and user feedback each require a team observation. Add the date, devices and result after the rehearsal.

[Project guidelines](https://chatgpt.com/library?file_id=libfile_e3b47df7e0b48191ad9a8aa95f46ebff&account_id=personal) · [Project paper](https://chatgpt.com/library?file_id=libfile_71d91beb4f4c81919fbbac3867ff1f14&account_id=personal)
