package com.example.bluetoothpracticetree.practicetree;

import android.os.Handler;

/*
    This class groups together the necessary Bulbs to create a single user tree. It is designed
    to make the process of updating the UI for the practice tree operations much more convenient
    by providing methods to automatically perform said operations.
*/

public class PracticeTree {

    private Bulb prestage;
    private Bulb stage;
    private Bulb topYellow;
    private Bulb midYellow;
    private Bulb botYellow;
    private Bulb green;
    private Bulb red;

    private boolean wentRed = false;

    public PracticeTree(Bulb prestage, Bulb stage, Bulb topYellow, Bulb midYellow, Bulb botYellow, Bulb green, Bulb red) {
        this.prestage = prestage;
        this.stage = stage;
        this.topYellow = topYellow;
        this.midYellow = midYellow;
        this.botYellow = botYellow;
        this.green = green;
        this.red = red;
    }


    public void setPrestage(boolean set) {
        prestage.setActive(set);
    }

    // This method updates the stage bulb and resets all other bulbs
    public void setStage(boolean set) {
        stage.setActive(set);
        wentRed = false;

        if (set) {
            topYellow.reset();
            midYellow.reset();
            botYellow.reset();
            green.reset();
            red.reset();
        }
    }

    // This method begins the proper bulb sequence of a real tree
    public void dropTree() {

        Handler handler = new Handler();
        if (!wentRed) {
            topYellow.activate();
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!wentRed) {
                    midYellow.activate();
                }
            }
        }, 500);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!wentRed) {
                    botYellow.activate();
                }
            }
        }, 1000);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!wentRed) {
                    green.persist();
                }
            }
        }, 1500);
    }

    // This method updates the practice tree if a user is disqualified
    public void goRed() {
        wentRed = true;
        red.persist();
        green.reset();

        if (topYellow.isActive()) {
            topYellow.persist();
        } else if (midYellow.isActive()) {
            midYellow.persist();
        } else if (botYellow.isActive()) {
            botYellow.persist();
        }
    }

    // This method flashes the tree to indicate a user won the race
    // NOTE: this method is unused at the moment
    public void win() {
        topYellow.activate();
        midYellow.activate();
        botYellow.activate();
        green.activate();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                topYellow.activate();
                midYellow.activate();
                botYellow.activate();
                green.activate();
            }
        }, 1000);
    }
}
