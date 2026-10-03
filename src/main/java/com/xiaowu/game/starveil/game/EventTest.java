package com.xiaowu.game.starveil.game;

import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.ui.core.GameUI;

public class EventTest {
    public void setupEventListeners() {
        GameUI gameUI = GameUI.getInstance();
        WorldMap worldMap = GameInstance.getWorldMap();
        worldMap.addEventListener("TestEvent", (event, args) -> {
            worldMap.switchMap("starveil:data/worlds/wu-home-floor2.json",1035.0, 130.0);
            event.completeCurrentEvent();
        });
        worldMap.addEventListener("ChangeToMap2", (event, args) -> {
             worldMap.switchMap("starveil:data/worlds/wu-home.json", 250.0, 700.0);
             event.completeCurrentEvent();
        });
    }
}
