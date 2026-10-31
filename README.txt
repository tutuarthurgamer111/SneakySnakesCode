=====================================================
  SneakySnakes v3 - Online Multiplayer Update
=====================================================

HOW TO RUN (Offline / Single-player):
  Windows:  double-click run.bat   OR  java -jar dist/SneakySnakes.jar
  Linux/Mac: bash run.sh

HOW TO RUN (Online Multiplayer):
  1. Install Node.js (https://nodejs.org)
  2. Open a terminal in the server/ folder:
       cd server
       npm install
       node server.js
  3. Launch SneakySnakes, click "Online Mode"
  4. Enter your name and the server address (default: ws://localhost:3000)
  5. Share the address with friends on the same network!

  To host publicly: use a VPS, or expose port 3000 via ngrok:
       ngrok tcp 3000
  Then share the ngrok address (change ws:// to match).

CONTROLS:
  WASD / Arrows        = Move
  Left Mouse Button    = Sprint (Online only)
  ENTER / SPACE        = Start game
  ESC                  = Pause / Back
  N                    = Toggle Day/Night
  LEFT / RIGHT         = Cycle skins (menu)
  S (on menu)          = Open Skin Shop
  Mouse Wheel          = Scroll shop
  F11                  = Fullscreen

MAIN MENU:
  Online Mode   = Multiplayer (requires server running)
  Offline Mode  = Classic single-player (unchanged)
  Skin Shop     = Buy skins with coins
  Mode Options  = Party Mode / Infinite Mode (coming soon)

MUSIC TRACKS:
  - track 1: Joshua McLean - Mountain Trials
  - track 2: Kevin MacLeod - 8bit Dungeon Boss
  - track 3: Kubbi - Up In My Jam
  - track 4: Cody O'Quinn - BATTLE MAN

=====================================================
  COMPILING FROM SOURCE
=====================================================
  cd src
  javac -cp . *.java
  jar cfe ../dist/SneakySnakes.jar SneakySnakes *.class
  cp -r ../SneakySnakes/Resources .
  cd ..
  java -jar dist/SneakySnakes.jar