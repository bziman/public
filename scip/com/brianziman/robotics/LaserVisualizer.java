/**
 * Visualization tool for Hokuyo URG-04LX Laser Sensor.
 * Copyright 2007 - Brian Ziman [bziman(at)acm.org]
 * http://www.brianziman.com/
 */
package com.brianziman.robotics;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;

import java.util.*;

/*  ^ 8192
 *  |        0
 *  | 90     *     -90
 *  |  120       -120
 *  v <----8192------->
 */
public class LaserVisualizer extends JPanel {

    double xmin = -4096;
    double xmax = 4096;
    double ymin = -4096;
    double ymax = 4096;

    private SCIP11 gLaser;
    private JFrame frame;

    private static boolean useLog = false;

    public static void main(String args[]) throws Exception {
        int port = 1701;
        for(int i=0;i<args.length;i++) {
            if(args[i].startsWith("-l")) {
                useLog = true;
            } else {
                port = Integer.parseInt(args[i]);
            }
        }
        LaserVisualizer lv = new LaserVisualizer(new SCIP11(port));
        for(;;) {
            lv.repaint();
            Thread.sleep(200);
        }
    }

    public LaserVisualizer(SCIP11 laser) {
        this.gLaser = laser;
        setDoubleBuffered(true);
        frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() { 
            public void windowClosing(WindowEvent e) { 
                System.exit(0); 
            }
        });
        frame.setTitle("Laser GUI");
        //setBounds(0,0,800,800);
        frame.setSize(800,800);
        frame.add(this);
        frame.setVisible(true);
    }

    Font font = new Font("FreeMono", Font.BOLD, 50);

    double maxrange = 0;
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0,0,getWidth(),getHeight());

        g.setFont(font);
        g.setColor(Color.WHITE);
        g.drawString("Range(0): "+gLaser.getRange(0), 20,70);

        // square!
        double scale = Math.min(getWidth(),getHeight()) / (useLog?2*Math.log(5000d):10000d);

        int x0 = getWidth() / 2;
        int y0 = getHeight() / 2;

        for(int i=-120;i<=120;i++) {
            double range = useLog?Math.log(4000):4000;
            int x1 = (int)(x0-scale*range*Math.sin(Math.toRadians(i)));
            int y1 = (int)(y0-scale*range*Math.cos(Math.toRadians(i)));
            g.setColor(Color.BLUE);
            g.drawLine(x0, y0, x1, y1);

            range = gLaser.getRange(i);
            if(range > maxrange) maxrange = range;
            if(range == 0) {
                range = maxrange;
                g.setColor(Color.YELLOW); // yellow = maybe = prob. under est.
            } else if(range < 20) {
                range = maxrange;
                g.setColor(Color.RED); // red = stop = just guessing
                //System.out.println(gLaser.getErrorFromData(range));
            } else {
                g.setColor(Color.GREEN); // green = go = good data
            }
            range = useLog?Math.log(range):range;
            x1 = (int)(x0-scale*range*Math.sin(Math.toRadians(i)));
            y1 = (int)(y0-scale*range*Math.cos(Math.toRadians(i)));
            g.drawLine(x0, y0, x1, y1);
            /*
            g.setColor(Color.BLUE);
            fillCircle(g, x0,y0, 10);
            g.setColor(Color.RED);
            double r = 50 * gPF.getNumParticles() * p.p; // make length of line proportional to fitness
            int x1 = (int)(x0 + r * Math.cos(p.a));
            int y1 = (int)(y0 - r * Math.sin(p.a)); // reverse y
            g.drawLine(x0, y0, x1, y1);
            g.setColor(Color.BLUE);
            fillCircle(g, x1,y1, (int)r); // draw circle at end of line
            */
            //System.err.println("("+x1+", "+y1+") = " + p.p);

        }
    }

    // draw a cirle of radius r centered at x, y
    // Normally, y is increasing as it goes down the screen.
    // We want to reverse that, so we can pass in normal
    // coordinates.
    private void fillCircle(Graphics g, int x, int y, int r) {
        int topleftx = x - r/2;
        int toplefty = y - r/2;
        g.fillOval(topleftx, toplefty, r, r);
    }


}
