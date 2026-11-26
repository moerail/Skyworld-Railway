package net.skyworld.skyatolite;

import java.util.Locale;

record LiteSign(String action,String value,String option,double number) {
    static boolean header(String text) {return text!=null && (text.trim().equalsIgnoreCase("[SAL]")||text.trim().equalsIgnoreCase("[SkyATOL]"));}
    static LiteSign parse(String second,String third,String fourth) {
        String action=second.trim().toLowerCase(Locale.ROOT),value=third.trim(),option=fourth.trim();
        double number=0;
        switch(action) {
            case "depot" -> {name(value);name(option);}
            case "awake" -> {name(value);var parts=option.split("@",-1);name(parts[0]);if(parts.length>2)throw new IllegalArgumentException("始发访问格式错误");if(parts.length==2)java.util.UUID.fromString(parts[1]);}
            case "station" -> {number=seconds(value);name(option);}
            case "message" -> {if(value.isBlank())throw new IllegalArgumentException("第三行需要显示文字");number=seconds(option);if(number<=0)throw new IllegalArgumentException("显示秒数须大于零");}
            case "property" -> {if(!value.equalsIgnoreCase("v_target"))throw new IllegalArgumentException("property 仅支持 V_target");number=speed(option);}
            case "v_target" -> {number=speed(value);if(!option.isBlank())throw new IllegalArgumentException("v_target 第四行留空；或使用 property / V_target / 速度");}
            default -> throw new IllegalArgumentException("未知 SAL 牌子类型");
        }
        return new LiteSign(action,value,option,number);
    }
    static double seconds(String text) {
        double result=Double.parseDouble(text);
        if(!Double.isFinite(result)||result<0||result>3600)throw new IllegalArgumentException("秒数须在 0–3600 之间");
        return result;
    }
    static double speed(String text) {
        String value=text.toLowerCase(Locale.ROOT).replace(" ","");double factor=1;
        for(String unit:new String[]{"km/h","kmh","m/s","m/t","b/t","mph"})if(value.endsWith(unit)) {
            factor=switch(unit){case "km/h","kmh"->1.0/72;case "m/s"->1.0/20;case "mph"->.44704/20;default->1;};
            value=value.substring(0,value.length()-unit.length());break;
        }
        double result=Double.parseDouble(value.replace(',','.'))*factor;
        if(!Double.isFinite(result)||result<0||result>100)throw new IllegalArgumentException("巡航速度无效");return result;
    }
    static void name(String name) {if(name.isBlank()||name.length()>64||!name.matches("[\\p{L}\\p{N}_-]+"))throw new IllegalArgumentException("名称只能含文字、数字、下划线或短横线");}
}
