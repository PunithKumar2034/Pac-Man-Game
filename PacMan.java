import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import javax.sound.sampled.*;
import javax.swing.*;

public class PacMan extends JPanel implements ActionListener, KeyListener {
    // Inner class representing game entities (Pacman, Ghosts, Walls, Food)
    class Block {
        int x, y, width, height, startX, startY;
        Image image;
        char direction = 'U'; 
        char pendingDirection = 'U'; 
        int velocityX = 0, velocityY = 0;

        Block(Image image, int x, int y, int width, int height) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.startX = x;
            this.startY = y;
        }

        // Sets movement speed based on current direction
        void updateVelocity() {
            if (this.direction == 'U') { velocityX = 0; velocityY = -tileSize/4; }
            else if (this.direction == 'D') { velocityX = 0; velocityY = tileSize/4; }
            else if (this.direction == 'L') { velocityX = -tileSize/4; velocityY = 0; }
            else if (this.direction == 'R') { velocityX = tileSize/4; velocityY = 0; }
        }

        // Returns entity to its original starting position
        void reset() {
            this.x = this.startX;
            this.y = this.startY;
            this.direction = 'U';
            this.pendingDirection = 'U';
            this.velocityX = 0;
            this.velocityY = 0;
        }
    }

    private int rowCount = 21, columnCount = 19, tileSize = 32;
    private int boardWidth = columnCount * tileSize, boardHeight = rowCount * tileSize;
    private Image wallImage, blueGhostImage, orangeGhostImage, pinkGhostImage, redGhostImage;
    private Image pacmanUpImage, pacmanDownImage, pacmanLeftImage, pacmanRightImage;
    private Clip bgMusic, eatSound, gameOverSound;

    // Map Legend: X = Wall, O = Void/Death, P = Pacman, Space = Food
    // Ghosts: b = blue, o = orange, p = pink, r = red
    private String[] tileMap = {
        "XXXXXXXXXXXXXXXXXXX", 
        "X        X        X", 
        "X XX XXX X XXX XX X", 
        "X                 X",
        "X XX X XXXXX X XX X", 
        "X    X       X    X", 
        "XXXX XXXX XXXX XXXX", 
        "OOOX X       X XOOO",
        "XXXX X XXrXX X XXXX", 
        "O       bpo       O", 
        "XXXX X XXXXX X XXXX", 
        "OOOX X       X XOOO",
        "XXXX X XXXXX X XXXX", 
        "X        X        X", 
        "X XX XXX X XXX XX X", 
        "X  X     P     X  X",
        "XX X X XXXXX X X XX", 
        "X    X   X   X    X", 
        "X XXXXXX X XXXXXX X", 
        "X                 X", 
        "XXXXXXXXXXXXXXXXXXX" 
    };

    HashSet<Block> walls, foods, ghosts;
    Block pacman;
    Timer gameLoop;
    char[] directions = {'U', 'D', 'L', 'R'};
    Random random = new Random();
    int score = 0;
    boolean gameOver = false;
    boolean gameStarted = false; 
    JButton restartButton, startButton;

    // Constructor: Sets up panel, loads assets, and initializes UI
    PacMan() {
        setPreferredSize(new Dimension(boardWidth, boardHeight));
        setBackground(new Color(61,40,29));
        addKeyListener(this);
        setFocusable(true);
        setLayout(null);

        // Load images with error handling
        try {
            wallImage = new ImageIcon(getClass().getResource("./Wall.png")).getImage();
            blueGhostImage = new ImageIcon(getClass().getResource("./blueGhost.png")).getImage();
            orangeGhostImage = new ImageIcon(getClass().getResource("./orangeGhost.png")).getImage();
            pinkGhostImage = new ImageIcon(getClass().getResource("./pinkGhost.png")).getImage();
            redGhostImage = new ImageIcon(getClass().getResource("./redGhost.png")).getImage();
            pacmanUpImage = new ImageIcon(getClass().getResource("./pacmanUp.png")).getImage();
            pacmanDownImage = new ImageIcon(getClass().getResource("./pacmanDown.png")).getImage();
            pacmanLeftImage = new ImageIcon(getClass().getResource("./pacmanLeft.png")).getImage();
            pacmanRightImage = new ImageIcon(getClass().getResource("./pacmanRight.png")).getImage();
        } catch (Exception e) { System.out.println("Error: Missing Image Files"); }

        loadSounds();

        // Menu Buttons
        startButton = new JButton("START GAME");
        styleButton(startButton);
        startButton.setBounds(boardWidth/2 - 75, boardHeight/2 + 20, 150, 45);
        startButton.addActionListener(e -> startGame());
        add(startButton);

        restartButton = new JButton("RESTART");
        styleButton(restartButton);
        restartButton.setBounds(boardWidth/2 - 60, boardHeight/2 + 20, 120, 40);
        restartButton.setVisible(false);
        restartButton.addActionListener(e -> restartGame());
        add(restartButton);

        loadMap();
        resetPositions(); 
        gameLoop = new Timer(50, this);
    }

    // Loads audio clips and lowers background volume for balance
    private void loadSounds() {
        // bgMusic = loadClip("fuzzball-parade-kevin-macleod-main-version-05-13-12971.wav");
        bgMusic = loadClip("hero.wav");
        gameOverSound = loadClip("cartoon-music-sting-wa-wa-wa-jam-fx-1-00-07.wav");
        eatSound = loadClip("mouth-pop-wet-bubble-the-foundation-1-00-01.wav");
        if (bgMusic != null) {
            FloatControl gain = (FloatControl) bgMusic.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(-1.0f); // Lower volume to hear 'Eat' sounds
        }
    }

    // Helper: Loads a sound file into a Clip object
    private Clip loadClip(String filename) {
        try {
            java.net.URL url = getClass().getResource(filename);
            if (url == null) return null;
            AudioInputStream ais = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Exception e) { return null; }
    }

    // Logic for playing sound effects or looping music
    private void playSound(Clip clip, boolean loop) {
        if (clip != null) {
            clip.setFramePosition(0);
            if (loop) clip.loop(Clip.LOOP_CONTINUOUSLY); else clip.start();
        }
    }

    // Custom UI styling for buttons
    private void styleButton(JButton btn) {
        btn.setFont(new Font("Arial", Font.BOLD, 14));
        // btn.setBackground(new Color(255, 215, 0));
        btn.setBackground(new Color(115, 72, 45)); 
        btn.setForeground(Color.BLACK);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
    }

    // Populates game world based on tileMap; adds food to every empty space
    public void loadMap() {
        walls = new HashSet<>(); foods = new HashSet<>(); ghosts = new HashSet<>();
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                char tile = tileMap[r].charAt(c);
                int x = c * tileSize, y = r * tileSize;
                if (tile == 'X') walls.add(new Block(wallImage, x, y, tileSize, tileSize));
                else if (tile == 'b') ghosts.add(new Block(blueGhostImage, x, y, tileSize, tileSize));
                else if (tile == 'o') ghosts.add(new Block(orangeGhostImage, x, y, tileSize, tileSize));
                else if (tile == 'p') ghosts.add(new Block(pinkGhostImage, x, y, tileSize, tileSize));
                else if (tile == 'r') ghosts.add(new Block(redGhostImage, x, y, tileSize, tileSize));
                else if (tile == 'P') pacman = new Block(pacmanRightImage, x, y, tileSize, tileSize);
                else if (tile == ' ') { 
                    foods.add(new Block(null, x + 12, y + 12, 8, 8)); // Food Everywhere
                }
            }
        }
    }

    // Main update logic: handles movement, collision, and score
    public void move() {
        if (!gameStarted || gameOver) return;

        // Death check for 'O' tiles (Void zones)
        int mx = (pacman.x + 16) / tileSize, my = (pacman.y + 16) / tileSize;
        if (my >= 0 && my < rowCount && mx >= 0 && mx < columnCount) {
            if (tileMap[my].charAt(mx) == 'O') { triggerGameOver(); return; }
        }

        // Pacman turn logic (only turns when centered on a tile)
        if (pacman.x % tileSize == 0 && pacman.y % tileSize == 0) {
            if (canMove(pacman, pacman.pendingDirection)) {
                pacman.direction = pacman.pendingDirection;
                updatePacmanImage();
            }
        }
        pacman.updateVelocity();
        pacman.x += pacman.velocityX; 
        pacman.y += pacman.velocityY;

        // Wall collision correction for Pacman
        for (Block wall : walls) {
            if (collision(pacman, wall)) { pacman.x -= pacman.velocityX; pacman.y -= pacman.velocityY; break; }
        }

        // Smart Ghost logic: predictive movement and anti-stacking
        for (Block ghost : ghosts) {
            if (collision(ghost, pacman)) { triggerGameOver(); return; }
            if (ghost.x % tileSize == 0 && ghost.y % tileSize == 0) {
                char newDir = getSmartGhostDirection(ghost);
                if (newDir != ' ') ghost.direction = newDir;
            }
            ghost.updateVelocity();
            ghost.x += ghost.velocityX; ghost.y += ghost.velocityY;
            
            
            for (Block wall : walls) {
                if (collision(ghost, wall)) { 
                    ghost.x -= ghost.velocityX; ghost.y -= ghost.velocityY; 
                    ghost.direction = directions[random.nextInt(4)]; 
                }
            }
            // Teleport logic for the tunnels
            if (ghost.x < 0) {
                ghost.x = boardWidth - ghost.width;
            } else if (ghost.x + ghost.width > boardWidth) {
                ghost.x = 0;
            }
            
        }

        // Eating logic and scoring (1 point per pellet)
        Block foodEaten = null;
        for (Block food : foods) {
            if (collision(pacman, food)) { foodEaten = food; score += 10; playSound(eatSound, false); }
        }
        if (foodEaten != null) foods.remove(foodEaten);
        if (foods.isEmpty()) { loadMap(); resetPositions(); 
        } // Level cleared reset
    }

    // Ghost pathfinding: evaluates moves to avoid walls, other ghosts, and U-turns
    public char getSmartGhostDirection(Block ghost) {
        char[] opts = {'U', 'D', 'L', 'R'};
        ArrayList<Character> possibleMoves = new ArrayList<>();
        for (char dir : opts) {
            int nx = ghost.x, ny = ghost.y;
            if (dir == 'U') ny -= tileSize; else if (dir == 'D') ny += tileSize;
            else if (dir == 'L') nx -= tileSize; else if (dir == 'R') nx += tileSize;

            Rectangle nextRect = new Rectangle(nx, ny, ghost.width, ghost.height);
            boolean blocked = false;
            for (Block wall : walls) if (nextRect.intersects(new Rectangle(wall.x, wall.y, wall.width, wall.height))) blocked = true;
            for (Block other : ghosts) if (other != ghost && nextRect.intersects(new Rectangle(other.x, other.y, other.width, other.height))) blocked = true;

            // Prevent immediate 180-degree turn
            boolean isRev = (ghost.direction == 'U' && dir == 'D') || (ghost.direction == 'D' && dir == 'U') ||
                            (ghost.direction == 'L' && dir == 'R') || (ghost.direction == 'R' && dir == 'L');

            if (!blocked && !isRev) possibleMoves.add(dir);
        }
        if (!possibleMoves.isEmpty()) return possibleMoves.get(random.nextInt(possibleMoves.size()));
        return getReverseDirection(ghost.direction);
    }

    // Forces ghost to turn around when hitting a dead end
    public char getReverseDirection(char dir) {
        if (dir == 'U') return 'D'; if (dir == 'D') return 'U';
        if (dir == 'L') return 'R'; if (dir == 'R') return 'L';
        return 'U';
    }

    // Swing boilerplate to trigger painting
    public void paintComponent(Graphics g) { super.paintComponent(g); draw(g); }

    // Renders all graphics, UI modals, and score text
    public void draw(Graphics g) {
        g.drawImage(pacman.image, pacman.x, pacman.y, pacman.width, pacman.height, null);
        for (Block ghost : ghosts) g.drawImage(ghost.image, ghost.x, ghost.y, ghost.width, ghost.height, null);
        for (Block wall : walls) g.drawImage(wall.image, wall.x, wall.y, wall.width, wall.height, null);
        g.setColor(Color.WHITE);
        for (Block food : foods) g.fillOval(food.x, food.y, food.width, food.height);
        
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.setColor(Color.YELLOW);
        g.drawString("Score: " + score, 15, 25);

        // Menu Overlays (Start/Game Over)
        if (!gameStarted || gameOver) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(new Color(0, 0, 0, 180)); g2d.fillRect(0, 0, boardWidth, boardHeight);
            int mX = (boardWidth - 320) / 2, mY = (boardHeight - 220) / 2;
            g2d.setColor(new Color(255, 215, 0)); g2d.fillRoundRect(mX - 2, mY - 2, 324, 224, 25, 25);
            g2d.setColor(new Color(30, 30, 30)); g2d.fillRoundRect(mX, mY, 320, 220, 25, 25);

            if (!gameStarted) {
                g2d.setColor(Color.YELLOW); g2d.setFont(new Font("Arial", Font.BOLD, 40));
                g2d.drawString("PAC-MAN", mX + (320 - g2d.getFontMetrics().stringWidth("PAC-MAN")) / 2, mY + 60);
                g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.PLAIN, 14));
                g2d.drawString("Use WASD or Arrows to move", mX + (320 - g2d.getFontMetrics().stringWidth("Use WASD or Arrows to move")) / 2, mY + 95);
            } else if (gameOver) {
                g2d.setColor(Color.RED); g2d.setFont(new Font("Arial", Font.BOLD, 36));
                g2d.drawString("GAME OVER", mX + (320 - g2d.getFontMetrics().stringWidth("GAME OVER")) / 2, mY + 60);
                g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.PLAIN, 20));
                g2d.drawString("Final Score: " + score, mX + (320 - g2d.getFontMetrics().stringWidth("Final Score: " + score)) / 2, mY + 100);
            }
        }
    }

    // Begins the game session and background audio
    public void startGame() { 
        gameStarted = true; startButton.setVisible(false); 
        gameLoop.start(); 
        playSound(bgMusic, true); 
        this.requestFocus(); 
    }

    // Stops game loop and plays Game Over sting
    public void triggerGameOver() { 
        gameOver = true; restartButton.setVisible(true); 
        gameLoop.stop(); if (bgMusic != null) bgMusic.stop(); 
        playSound(gameOverSound, false); repaint(); 
    }

    // Checks if the next move position overlaps with any walls
    public boolean canMove(Block b, char dir) {
        int nx = b.x, ny = b.y;
        if (dir == 'U') ny -= tileSize/4; else if (dir == 'D') ny += tileSize/4;
        else if (dir == 'L') nx -= tileSize/4; else if (dir == 'R') nx += tileSize/4;
        Rectangle pos = new Rectangle(nx, ny, b.width, b.height);
        for (Block wall : walls) if (pos.intersects(new Rectangle(wall.x, wall.y, wall.width, wall.height))) return false;
        return true;
    }

    // Swaps image resource based on current movement direction
    public void updatePacmanImage() {
        if (pacman.direction == 'U') pacman.image = pacmanUpImage;
        else if (pacman.direction == 'D') pacman.image = pacmanDownImage;
        else if (pacman.direction == 'L') pacman.image = pacmanLeftImage;
        else if (pacman.direction == 'R') pacman.image = pacmanRightImage;
    }

    // Intersection check helper for all game blocks
    public boolean collision(Block a, Block b) { 
        return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y; 
    }

    // Centers all actors and resets ghost movement vectors
    public void resetPositions() { 
        pacman.reset(); for (Block ghost : ghosts) { 
            ghost.reset(); 
            ghost.direction = directions[random.nextInt(4)]; 
            ghost.updateVelocity(); 
        } 
    }

    // Full state reset for starting a new game
    public void restartGame() { score = 0; loadMap(); resetPositions(); gameOver = false; restartButton.setVisible(false); gameLoop.start(); playSound(bgMusic, true); this.requestFocus(); }

    @Override 
    public void actionPerformed(ActionEvent e) { 
        move(); repaint(); 
    }
    
    // Monitors keyboard input for movement commands
    @Override 
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) pacman.pendingDirection = 'U';
        else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) pacman.pendingDirection = 'D';
        else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) pacman.pendingDirection = 'L';
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) pacman.pendingDirection = 'R';
    }
    @Override 
    public void keyTyped(KeyEvent e) {}
    @Override
    public void keyPressed(KeyEvent e) {}
}