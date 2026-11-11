package com.fiscuvia.identity.internal;

import java.util.Locale;

/** Approved only for the controlled local issuer. No aliases, email autolink or identity authority. */
record LocalContact(String email,String name) {
    static String canonicalEmail(String value) {
        if(value==null || value.length()>254 || !value.equals(value.strip())
            || !value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))return null;
        int split=value.lastIndexOf('@');return value.substring(0,split+1)+value.substring(split+1).toLowerCase(Locale.ROOT);
    }
    static LocalContact verified(String email,String name,boolean verified) {
        String canonical=verified?canonicalEmail(email):null;
        if(canonical==null)return new LocalContact(null,null);
        String display=name==null?null:name.strip();
        if(display!=null && (display.isEmpty() || display.length()>160 || display.codePoints().anyMatch(Character::isISOControl)))display=null;
        return new LocalContact(canonical,display);
    }
    @Override public String toString(){return "LocalContact[private]";}
}
