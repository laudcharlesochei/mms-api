package uk.ac.dundee.ga.mms;

import org.junit.jupiter.api.Test;
import uk.ac.dundee.ga.mms.util.Csv;
import uk.ac.dundee.ga.mms.util.NotesCipher;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.util.Totp;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest {

    @Test
    void totpMatchesRfc6238Vector() {
        // RFC 6238 test secret "12345678901234567890" in base32, T = 59 s -> 94287082 (8 digits) -> 287082 (6 digits)
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(Totp.generate(secret, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.verify(secret, "287082", 59)).isTrue();
        assertThat(Totp.verify(secret, "000000", 59)).isFalse();
    }

    @Test
    void notesAreEncryptedAndDecrypted() {
        NotesCipher.init("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String enc = NotesCipher.encrypt("Worried about exam timing.");
        assertThat(enc).startsWith("enc:v1:").doesNotContain("exam");
        assertThat(NotesCipher.decrypt(enc)).isEqualTo("Worried about exam timing.");
    }

    @Test
    void htmlIsStrippedOnSave() {
        assertThat(TextSanitizer.clean("  <script>alert(1)</script>Hello <b>there</b> ")).isEqualTo("Hello there");
        assertThat(TextSanitizer.clean("   ")).isNull();
    }

    @Test
    void csvEscapesAndBlocksFormulas() {
        assertThat(Csv.row(Arrays.asList("a,b", "=SUM(A1)", null, 3))).isEqualTo("\"a,b\",'=SUM(A1),,3\r\n");
    }
}
