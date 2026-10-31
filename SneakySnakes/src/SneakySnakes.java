import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SneakySnakes {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SneakySnakes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel gamePanel = new GamePanel();
            frame.add(gamePanel);
            frame.pack();

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(
                (screenSize.width  - frame.getWidth())  / 2,
                (screenSize.height - frame.getHeight()) / 2
            );

            frame.setVisible(true);
            gamePanel.requestFocusInWindow();

            GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();

            frame.addKeyListener(new KeyAdapter() {
                boolean isFullscreen = false;
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_F11) {
                        isFullscreen = !isFullscreen;
                        frame.dispose();
                        frame.setUndecorated(isFullscreen);
                        if (isFullscreen) {
                            gd.setFullScreenWindow(frame);
                        } else {
                            gd.setFullScreenWindow(null);
                            frame.pack();
                            frame.setLocation(
                                (screenSize.width  - frame.getWidth())  / 2,
                                (screenSize.height - frame.getHeight()) / 2
                            );
                            frame.setVisible(true);
                        }
                        gamePanel.requestFocusInWindow();
                    }
                }
            });
        });
    }
}