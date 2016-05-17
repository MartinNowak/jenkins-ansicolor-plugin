package hudson.plugins.ansicolor;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleAnnotatorFactory;

/**
 * Converts ANSI codes in the console output to html markup.
 *
 * @author Martin Nowak
 */
@Extension /*@Symbol("ansi")*/
public class AnsiColorAnnotator extends ConsoleAnnotatorFactory<Object> {
    @Override
    public ConsoleAnnotator newInstance(Object context) {
        return new AnsiColorAnnotatorImpl();
    }

    private static class AnsiColorAnnotatorImpl extends ConsoleAnnotator {
        public ConsoleAnnotator annotate(Object context, MarkupText text) {
            String str = text.getText();
            for (int i = 0; i < str.length(); ++i) {
                char c = str.charAt(i);
                if (c != ESC || i + 1 == str.length() || str.charAt(i + 1) != '[') // CSI
                    continue;
                int end = detectSGR(str, i);
                if (end > i) {
                    interpretSGR(str, i, end, text);
                    i = end;
                }
            }
            return this;
        }

        // https://en.wikipedia.org/wiki/ANSI_escape_code#SGR ESC[12;;32m ESC[m ESC[0m
        private int detectSGR(String str, int beg) {
            for (int i = beg + 2; i < str.length(); ++i) {
                char c = str.charAt(i);
                if (c >= '0' && c <= '9' || c == ';')
                    continue;
                else if (c == 'm')
                    return i;
                else
                    break;
            }
            return beg; // only interested in SGR commands
        }

        private void interpretSGR(String str, int beg, int end, MarkupText text) {
            int ofc=fc, obc=bc;
            boolean obo=bo, ofa=fa, oita=ita, oul=ul, obl=bl, oinv=inv, ocon=con;

            int param=0;
            for (int i = beg + 2; i < end; ++i) {
                char c = str.charAt(i);
                if (c >= '0' && c <= '9')
                    param = 10 * param + c - '0';
                else if (c == ';') {
                    interpretSGRParam(param);
                    param = 0;
                }
            }
            interpretSGRParam(param);

            // close open span
            if (ofc != 9 || obc != 9 || obo || ofa || oita || oul || obl || oinv || ocon)
                text.addMarkup(beg, "</span>");

            // hide CSI SGR code
            text.hide(beg, end+1);

            // open new span
            if (fc != 9 || bc != 9 || bo || fa || ita || ul || bl || inv || con)
            {
                String tag = "<span style=\"";
                if (inv)
                {
                    // TODO: need to know default fg/bg color in order to explicitly set them, assume white on black for now
                    tag += "color:"+colorMap[bc == 9 ? 0 : bc]+";";
                    tag += "background-color:"+colorMap[fc == 9 ? 7 : fc]+";";
                }
                else
                {
                    if (fc != 9) tag += "color:"+colorMap[fc]+";";
                    if (bc != 9) tag += "background-color:"+colorMap[bc]+";";
                }
                if (bo) tag += "font-weight:bold;";
                if (fa) tag += "font-weight:light;";
                if (ita) tag += "font-style:italic;";
                if (ul) tag += "text-decoration:underline;";
                if (bl) tag += "text-decoration:blink;";
                if (con) tag += "display:hidden;";
                tag += "\">";
                text.addMarkup(end+1, tag);
            }
        }

        // https://en.wikipedia.org/wiki/ANSI_escape_code#graphics
        // https://github.com/theZiz/aha
        private void interpretSGRParam(int param) {
            switch (param) {
            case 0: fc=9; bc=9; bo=false; bl=false; ul=false; break;
            case 1: bo = true; break;
            case 3: ita = true; break;
            case 4: ul = true; break;
            case 5: case 6: bl = true; break;
            case 7: inv = true; break;
            case 8: con = true; break;
            case 21: bo = false; break;
            case 23: ita = false; break;
            case 24: ul = false; break;
            case 25: bl = false; break;
            case 27: inv = false; break;
            case 28: con = false; break;
            case 30: case 31: case 32: case 33: case 34: case 35: case 36: case 37: case 39: fc = (byte) (param - 30); break;
            case 40: case 41: case 42: case 43: case 44: case 45: case 46: case 47: case 49: bc = (byte) (param - 40); break;
            case 90: case 91: case 92: case 93: case 94: case 95: case 96: case 97: fc = (byte) (param - 90); break;
            case 100: case 101: case 102: case 103: case 104: case 105: case 106: case 107: bc = (byte) (param - 100); break;
            default:
                break;
            }
        }

        private static char ESC = '\u001B';
        private static String[] colorMap = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};

        private byte fc=9, bc=9; // foreground, background (9 represents default, i.e. no markup)
        private boolean bo, fa, ita, ul, bl, inv, con; // bold, faint, italic, underline, blink, inverse, conceal
    }
}
