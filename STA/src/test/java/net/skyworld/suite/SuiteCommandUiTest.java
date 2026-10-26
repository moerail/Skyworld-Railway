package net.skyworld.suite;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

public final class SuiteCommandUiTest {
    public interface LocalizedPlugin extends Plugin {
        String commandUiLanguage(CommandSender sender);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    public static void main(String[] args) {
        for (String root : List.of("st", "stcs", "sta", "skypcc")) {
            for (String lang : List.of("zh", "en", "fr", "ja", "ru")) {
                List<String> all = new ArrayList<>();
                for (int page=1; page<=SuiteCommandUi.pageCount(root); page++) {
                    List<String> lines=SuiteCommandUi.helpLines(root,lang,page);
                    assert lines.size()>=4 && lines.size()<=11;
                    assert lines.stream().noneMatch(String::isBlank);
                    all.addAll(lines);
                }
                assert all.stream().anyMatch(s -> s.contains("version"));
                if (root.equals("st")) assert all.stream().anyMatch(s -> s.contains("syncstatus"));
                if (root.equals("stcs")) assert all.stream().anyMatch(s -> s.contains("ma fs|release"));
            }
        }
        assert SuiteCommandUi.normalize("jp").equals("ja");
        assert SuiteCommandUi.normalize("fr_FR").equals("fr");
        assert SuiteCommandUi.normalize("ru_RU").equals("ru");
        assert SuiteCommandUi.normalize("ru-RU").equals("ru");
        assert SuiteCommandUi.complete("st", new String[]{"help","r"}).equals(List.of("ru"));
        assert SuiteCommandUi.complete("st", new String[]{"version","r"}).equals(List.of("ru"));
        assert SuiteCommandUi.complete("st", new String[]{"help",""}).contains("5");
        assert SuiteCommandUi.complete("stcs", new String[]{"version","f"}).equals(List.of("fr"));
        List<String> output=new ArrayList<>();
        CommandSender sender=proxy(CommandSender.class, (p,m,a)-> {
            if (m.getName().equals("sendMessage")) output.add((String)a[0]);
            return null;
        });
        Map<String,Plugin> plugins=new HashMap<>();
        PluginManager manager=proxy(PluginManager.class,(p,m,a)->m.getName().equals("getPlugin")?plugins.get(a[0]):null);
        Server server=proxy(Server.class,(p,m,a)->m.getName().equals("getPluginManager")?manager:null);
        LocalizedPlugin plugin=proxy(LocalizedPlugin.class,(p,m,a)->switch(m.getName()) {
            case "getServer" -> server;
            case "commandUiLanguage" -> "fr";
            case "isEnabled" -> true;
            case "getDescription" -> new PluginDescriptionFile("SkyTrainFolia","9.8.7-test","example.Main");
            default -> null;
        });
        plugins.put("SkyTrainFolia",plugin);
        assert SuiteCommandUi.language(plugin,sender).equals("fr");
        assert SuiteCommandUi.handle(plugin,sender,"st",new String[]{"version"});
        assert output.stream().anyMatch(s->s.contains("9.8.7-test"));
        assert output.stream().anyMatch(s->s.contains("Non installé"));
        output.clear();
        SuiteCommandUi.handle(plugin,sender,"st",new String[]{"help","2","en"});
        assert output.getFirst().equals("/st Help 2/5");
        assert SuiteCommandUi.language(plugin,sender).equals("fr");
        output.clear();
        SuiteCommandUi.handle(plugin,sender,"st",new String[]{"help","1","ru"});
        assert output.getFirst().equals("/st Справка 1/5");
        assert output.stream().anyMatch(s -> s.contains("Получить управление"));
        assert SuiteCommandUi.language(plugin,sender).equals("fr");
        output.clear();
        SuiteCommandUi.handle(plugin,sender,"st",new String[]{"version","ru"});
        assert output.stream().anyMatch(s -> s.contains("Не установлен"));
        output.clear();
        SuiteCommandUi.handle(plugin,sender,"st",new String[]{"help","999"});
        assert output.getFirst().contains("Arguments invalides");
        assert !SuiteCommandUi.handle(plugin,sender,"st",new String[]{"p1"});
        System.out.println("PASS five-language pages, live metadata, optional plugins, language preference/override and completion");
    }
}
