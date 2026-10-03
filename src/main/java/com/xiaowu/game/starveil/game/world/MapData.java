package com.xiaowu.game.starveil.game.world;

import java.util.List;
import java.util.Map;

public class MapData {
     
    public Map<String, MapInfo> maps;

    public static class MapInfo {
        public String id;           
        public String map;          
        public List<Button> button;

        public static class Button {
            public String title;
            public double x;
            public double y;
            public int    id;       
        }
    }
}