package com.fiscuvia.taxpayers.internal;

import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.ApiFailure;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;

/** Local syntax/structure only. No checksum inference, name reconstruction or network access. */
final class TaxpayerValidation {
    private TaxpayerValidation() {}
    static String rfc(String input,String person) {
        if(input==null||input.length()>32||input.codePoints().anyMatch(Character::isISOControl))throw invalid();
        int start=0,end=input.length();while(start<end&&input.charAt(start)==' ')start++;while(end>start&&input.charAt(end-1)==' ')end--;
        String canonical=Normalizer.normalize(input.substring(start,end),Normalizer.Form.NFC);
        if(!canonical.matches("[A-Za-zÑñ&0-9]+"))throw invalid();
        String rfc=canonical.toUpperCase(Locale.ROOT);
        int prefix=switch(person==null?"":person){case "INDIVIDUAL"->4;case "LEGAL_ENTITY"->3;default->0;};
        if(prefix==0||!rfc.matches("[A-ZÑ&]{"+prefix+"}[0-9]{6}[A-Z0-9]{3}")
            ||rfc.equals("XAXX010101000")||rfc.equals("XEXX010101000"))throw invalid();
        try {LocalDate.of(2000+Integer.parseInt(rfc.substring(prefix,prefix+2)),Integer.parseInt(rfc.substring(prefix+2,prefix+4)),Integer.parseInt(rfc.substring(prefix+4,prefix+6)));}
        catch(DateTimeException|NumberFormatException badDate){throw invalid();}
        return rfc;
    }
    static String name(String input,int max,boolean optional) {
        if(input==null){if(optional)return null;throw invalid();}
        if(input.codePoints().anyMatch(Character::isISOControl))throw invalid();
        String name=Normalizer.normalize(input.strip(),Normalizer.Form.NFC);
        if(name.isEmpty()||name.codePointCount(0,name.length())>max)throw invalid();
        return name;
    }
    static ApiFailure invalid(){return new ApiFailure(ApiErrorCode.VALIDATION_FAILED);}
}
