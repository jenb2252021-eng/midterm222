# The Lorax · Baguio Forest Registry

The Lorax is a JavaFX desktop registry for reforestation teams on a shared local network. The server stores the records, photos and review decisions. A ranger places a pin, records measurements and submits a tree. An administrator reviews the submission before it appears in the inventory.

## Run in VS Code

1. Open this folder with JDK 25 and the Java Extension Pack installed. Allow Maven project import to finish.
2. Open **Run and Debug**, select **The Lorax · Demonstration**, and press **F5**. This profile uses the separate `demo-data` folder. **The Lorax · Existing records** opens the original data. Stop other Lorax servers before switching datasets because clients automatically discover a running server.
3. On the host, enter administrator setup. For a new dataset, use the reserved name **admin** and choose a password of at least eight characters. Start the server and open the registry. For an existing dataset, sign in with the existing account. The updated app migrates its passwords to salted hashes on the server.
4. On another laptop on the same LAN, choose **The Lorax · Join a server**, connect to the host, and use **New ranger? Create an account**. If discovery fails, use the host address shown by the launcher. The default RMI registry port is 1099, the services use 1100–1103, and discovery uses UDP 50000. Both laptops must run the same version.

The Maven alternative for the existing dataset is `.\mvnw.cmd javafx:run` from the project directory with JAVA_HOME set to JDK 25. Initial dependency setup can require internet. For maps without internet, select **Offline** within the bundled Baguio coverage. The local server must remain reachable for registry operations. RMI traffic is unencrypted.

## Three-minute presentation

- Open the demo inventory. Its four tree records are fictional examples based on the Figma prototype. Counts and map pins come from the records. Unphotographed trees show a leaf placeholder.
- As a ranger, choose **Register Sapling**. Enter species, measurements and planting date. Attach a tree photo you have permission to show, place a pin inside Baguio, and choose **Log to Forest**.
- Open **My submissions** as the ranger. Show the pending entry. On the admin client, open **Pending Review**, inspect the measurements and photo, then approve it.
- Return to **Registry**, search the species or ID, and show the approved record on the **Field Map**. Open **System States** on the admin client to see the server activity log.

The old paper's Chapter 5 results describe its November 7 build. They are historical results and should be updated after your team exercises this version on its presentation laptops.

## Final project materials

- [BRD addendum and verification matrix](docs/BRD-addendum.md)
- [Defense guide and two-laptop rehearsal](docs/Defense-guide.md)
