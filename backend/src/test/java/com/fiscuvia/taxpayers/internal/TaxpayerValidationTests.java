package com.fiscuvia.taxpayers.internal;

import com.fiscuvia.platform.ApiFailure;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaxpayerValidationTests {
    @Test void personLengthAndAlphabetAreEnforced(){
        for(String rfc:new String[]{"DEM000101001","Ñ&Z000101ABC"})assertThat(TaxpayerValidation.rfc(rfc,"LEGAL_ENTITY")).isEqualTo(rfc);
        for(String rfc:new String[]{"DEMO000101ABC","Ñ&ZA000101001"})assertThat(TaxpayerValidation.rfc(rfc,"INDIVIDUAL")).isEqualTo(rfc);
        for(String rfc:new String[]{"DEM000101AB","DEMO000101ABC","DÉM000101ABC","ＤEM000101ABC","DEM-000101AB","DEM000101A B","DEſ000101ABC","Dß000101ABC"})
            assertThatThrownBy(()->TaxpayerValidation.rfc(rfc,"LEGAL_ENTITY")).isInstanceOf(ApiFailure.class);
    }
    @Test void impossibleDatesAreRejectedButCenturyIsNotInvented(){
        for(String rfc:new String[]{"DEM010229ABC","DEM000230ABC","DEM000431ABC","DEM001301ABC","DEM000100ABC"})
            assertThatThrownBy(()->TaxpayerValidation.rfc(rfc,"LEGAL_ENTITY")).isInstanceOf(ApiFailure.class);
        assertThat(TaxpayerValidation.rfc("DEM000229ABC","LEGAL_ENTITY")).isEqualTo("DEM000229ABC");
        assertThat(TaxpayerValidation.rfc("DEM040229ABC","LEGAL_ENTITY")).isEqualTo("DEM040229ABC");
    }
    @Test void onlyTheDocumentedGenericValuesAreRejected(){
        for(String rfc:new String[]{"XAXX010101000","XEXX010101000"})assertThatThrownBy(()->TaxpayerValidation.rfc(rfc,"INDIVIDUAL")).isInstanceOf(ApiFailure.class);
        assertThat(TaxpayerValidation.rfc("XAXX010101AB1","INDIVIDUAL")).isEqualTo("XAXX010101AB1");
        assertThat(TaxpayerValidation.rfc("XEXX010101AB1","INDIVIDUAL")).isEqualTo("XEXX010101AB1");
    }
    @Test void normalizationPreservesPermittedSpecialCharactersAndRejectsControls(){
        assertThat(TaxpayerValidation.rfc("  n\u0303&z000101ab1  ","LEGAL_ENTITY")).isEqualTo("Ñ&Z000101AB1");
        assertThatThrownBy(()->TaxpayerValidation.rfc("\tDEM000101ABC","LEGAL_ENTITY")).isInstanceOf(ApiFailure.class);
        assertThatThrownBy(()->TaxpayerValidation.rfc("DEM000101ABC",null)).isInstanceOf(ApiFailure.class);
    }
    @Test void namesUseCodePointBoundsWithoutErasingInput(){
        assertThat(TaxpayerValidation.name("  Nombre DEMO  ",300,false)).isEqualTo("Nombre DEMO");
        assertThat(TaxpayerValidation.name(null,200,true)).isNull();
        for(String name:new String[]{"   ","a".repeat(301),"Nombre\nDEMO"})assertThatThrownBy(()->TaxpayerValidation.name(name,300,false)).isInstanceOf(ApiFailure.class);
    }
}
