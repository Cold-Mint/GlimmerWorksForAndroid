package com.coldmint.glimmerworks;

import org.libsdl.app.SDLActivity;

public class MainActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        return new String[]{
                "fmt",
                "SDL3",
                "SDL3_image",
                "SDL3_ttf",
                "box2d",
                "GlimmerWorks",
        };
    }
}