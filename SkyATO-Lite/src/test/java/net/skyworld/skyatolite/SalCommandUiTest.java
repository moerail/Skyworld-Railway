package net.skyworld.skyatolite;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SalCommandUiTest {
    public static void main(String[] args) {
        var output = new ArrayList<String>();
        var sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
            new Class<?>[]{CommandSender.class}, (p,m,a) -> {
                if(m.getName().equals("sendMessage")) output.add((String)a[0]);
                return null;
            });
        SalCommandUi.send(sender,"§cInvalid route");
        assert output.getFirst().equals("§9[SkyATO-Lite] §r§f§cInvalid route");
        var driver = SalCommandUi.help(false);
        assert driver.stream().anyMatch(s -> s.contains("/sal release"));
        assert driver.stream().noneMatch(s -> s.contains("/sal awake") || s.contains("/sal route"));
        var admin = SalCommandUi.help(true);
        assert admin.stream().anyMatch(s->s.contains("/sal override"));assert driver.stream().noneMatch(s->s.contains("/sal override"));
        for(var command:new String[]{"create","direction","bind","choose","list","remove","preview","validate","publish"})
            assert admin.stream().anyMatch(s -> s.contains("/sal route "+command));
        var rich = (TextComponent)SalCommandUi.component("handover");
        assert rich.content().equals("[SkyATO-Lite] ");
        assert rich.color().equals(NamedTextColor.BLUE);
        assert ((TextComponent)rich.children().getFirst()).content().equals("handover");
        System.out.println("PASS SAL blue prefix, error colour, grouped administrator/driver help and rich handover prefix");
    }
}
