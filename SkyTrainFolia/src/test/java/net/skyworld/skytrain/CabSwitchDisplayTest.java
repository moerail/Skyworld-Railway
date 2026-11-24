package net.skyworld.skytrain;

public final class CabSwitchDisplayTest {
    static StcsTrackSnapshot snapshot(String state, String direction) {
        return new StcsTrackSnapshot("edge","a","b",0.,100.,1,"L",0.,"balise","B",100.,100.,
                "S1",200.,"world 200 64 0",200.,state,direction);
    }
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        assert CabSidebar.LINE_COUNT == 15;
        UiLanguage[] languages = {UiLanguage.ZH,UiLanguage.EN,UiLanguage.FR,UiLanguage.JP,UiLanguage.RU};
        String[][] expected = {{"直","左","右","未知","转换中"},
                {"Straight","Left","Right","Unknown","Changing"},
                {"Tout droit","Gauche","Droite","Inconnu","En mouvement"},
                {"直進","左","右","不明","転換中"},
                {"Прямо","Налево","Направо","Неизвестно","Перевод"}};
        for (int i=0;i<languages.length;i++) {
            String[] states={"STRAIGHT","DIVERGING","DIVERGING","UNKNOWN","PENDING"};
            String[] directions={"STRAIGHT","LEFT","RIGHT","STRAIGHT","LEFT"};
            for(int j=0;j<states.length;j++) {
                var s=snapshot(states[j],directions[j]);
                String display=CabUiManager.nextSwitchDisplay(languages[i],s);
                assert display.startsWith("S1 [") && display.endsWith(": "+expected[i][j]+"]") : display;
                assert !display.contains("world") && !display.contains("200") && !display.contains("\n");
                assert s.nextSwitchDistanceMeters()==200.;
                assert s.withEstimatedOffset(10).nextSwitchDirection().equals(s.nextSwitchDirection());
            }
            assert CabUiManager.nextSwitchDisplay(languages[i],StcsTrackSnapshot.unavailable()).equals("--");
            assert CabUiManager.nextSwitchDisplay(languages[i],snapshot(null,null)).endsWith(": "+expected[i][3]+"]");
        }
        var legacy=new net.skyworld.sta.api.v5.RailNetworkService.Navigation(null,null,null,null,null,"S1",null,null,200.);
        assert legacy.nextSwitchState().equals("UNKNOWN") && legacy.nextSwitchDistanceMeters()==200.;
        System.out.println("PASS five-language single-row switch direction, unknown/pending, distance and 15-line budget");
    }
}
