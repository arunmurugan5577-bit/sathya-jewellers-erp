import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;

/**
 * Prints labels on the shop's printer for a server that is somewhere else.
 *
 * <p>The application cannot spool anything once it is hosted: the printers it
 * can see belong to a data centre. So it renders each page to a bitmap and
 * leaves it in a queue, and this sits on the counter PC, collects the pages and
 * puts them on the real printer.
 *
 * <p>It deliberately has no dependencies and no build. Copy this one file to
 * the shop PC with a Java runtime and start it:
 *
 * <pre>
 *   java PrintAgent.java agent.properties
 * </pre>
 *
 * <p>Everything about how a tag looks - layout, sizes, the logo, calibration -
 * is decided on the server. This end receives a bitmap and a paper size, and
 * has nothing left to get wrong.
 */
public final class PrintAgent {

    static final String VERSION = "1.0.0";

    /** Idle poll. Fast enough that a counter does not wait, light on the server. */
    static final Duration IDLE_POLL = Duration.ofSeconds(3);
    /** After a failure, back off rather than hammering a server that is down. */
    static final Duration ERROR_POLL = Duration.ofSeconds(15);
    /** Pages fetched per round. */
    static final int BATCH = 5;

    private final String baseUrl;
    private final String username;
    private final String password;
    /** The printer this PC should use when the server does not name one. */
    private final String configuredPrinter;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private String token;

    PrintAgent(String baseUrl, String username, String password, String configuredPrinter) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.configuredPrinter = configuredPrinter == null ? "" : configuredPrinter.trim();
    }

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "agent.properties");
        if (!Files.exists(configPath)) {
            writeSampleConfig(configPath);
            System.out.println("Wrote a starter " + configPath.toAbsolutePath());
            System.out.println("Fill in the server address and the agent's sign-in, then run this again.");
            return;
        }
        Properties config = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            config.load(in);
        }
        String url = required(config, "server.url");
        String user = required(config, "agent.username");
        String pass = required(config, "agent.password");
        // Optional. Without it the agent falls back to whatever Windows calls
        // the default printer, which is a machine-wide setting anyone can
        // change - not something label printing should depend on.
        String printer = config.getProperty("printer.name", "").trim();

        System.out.println("Sathya Jewellers print agent " + VERSION);
        System.out.println("  server : " + url);
        System.out.println("  user   : " + user);
        List<String> printers = localPrinters();
        System.out.println("  printers on this PC:");
        for (String p : printers) {
            System.out.println("    - " + p + (p.equalsIgnoreCase(printer) ? "   <- configured" : ""));
        }
        if (printer.isEmpty()) {
            PrintService fallback = PrintServiceLookup.lookupDefaultPrintService();
            System.out.println("  printer    : not set, so Windows' default ("
                    + (fallback == null ? "none installed" : fallback.getName()) + ")");
            System.out.println("               Set printer.name in the configuration to pin one.");
        } else if (printers.stream().noneMatch(p -> p.equalsIgnoreCase(printer))) {
            System.out.println("  printer    : \"" + printer + "\" is NOT installed on this PC.");
            System.out.println("               Printing will fail until the name matches one above.");
        } else {
            System.out.println("  printer    : " + printer);
        }
        System.out.println("Working. Leave this window open. Ctrl+C to stop.");
        new PrintAgent(url, user, pass, printer).run();
    }

    private static String required(Properties config, String key) {
        String value = config.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is missing from the configuration file");
        }
        return value;
    }

    private static void writeSampleConfig(Path path) throws IOException {
        String sample = """
                # Where the hosted application lives, including /api
                server.url=https://erp.example.com/api

                # A user account kept for this agent alone. It needs the
                # "Print barcode labels" permission and nothing else.
                agent.username=print-agent
                agent.password=

                # Which printer on this PC the labels go to. Leave blank to use
                # whatever Windows calls the default - but naming it here means
                # tags cannot end up on the office printer because someone
                # changed the default.
                printer.name=Bar Code Printer T-9650 Plus
                """;
        Files.writeString(path, sample, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- the loop ---

    void run() {
        while (true) {
            Duration wait = IDLE_POLL;
            try {
                if (token == null) {
                    login();
                }
                long pending = heartbeat();
                if (pending > 0) {
                    int printed = collectAndPrint();
                    // Straight round again while there is a backlog: a counter
                    // waiting on a tag should not also wait on a poll interval.
                    wait = printed > 0 ? Duration.ZERO : IDLE_POLL;
                }
            } catch (Unauthorised ex) {
                log("sign-in expired, signing in again");
                token = null;
                wait = Duration.ofSeconds(1);
            } catch (Exception ex) {
                log("cannot reach the server: " + ex.getMessage());
                wait = ERROR_POLL;
            }
            sleep(wait);
        }
    }

    private void login() throws IOException, InterruptedException {
        String body = "{\"username\":%s,\"password\":%s}".formatted(quote(username), quote(password));
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        if (response.statusCode() != 200) {
            throw new IOException("sign-in refused (" + response.statusCode() + "). Check the agent's username and password.");
        }
        token = field(response.body(), "accessToken");
        if (token == null) {
            throw new IOException("the server did not return an access token");
        }
        log("signed in");
    }

    private long heartbeat() throws IOException, InterruptedException {
        StringBuilder printers = new StringBuilder("[");
        List<String> names = localPrinters();
        for (int i = 0; i < names.size(); i++) {
            printers.append(i == 0 ? "" : ",").append(quote(names.get(i)));
        }
        printers.append("]");
        PrintService fallback = PrintServiceLookup.lookupDefaultPrintService();
        String body = "{\"agentVersion\":%s,\"hostName\":%s,\"printers\":%s,\"defaultPrinter\":%s}".formatted(
                quote(VERSION), quote(hostName()), printers,
                quote(fallback == null ? "" : fallback.getName()));

        HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/labels/agent/heartbeat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        String pending = field(response.body(), "pending");
        return pending == null ? 0 : Long.parseLong(pending);
    }

    private int collectAndPrint() throws IOException, InterruptedException {
        HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/labels/agent/claim?max=" + BATCH))
                .POST(HttpRequest.BodyPublishers.noBody()));

        int printed = 0;
        for (String page : splitObjects(response.body())) {
            String id = field(page, "id");
            if (id == null) {
                continue;
            }
            try {
                byte[] png = Base64.getDecoder().decode(field(page, "imageBase64"));
                double widthMm = Double.parseDouble(field(page, "widthMm"));
                double heightMm = Double.parseDouble(field(page, "heightMm"));
                spool(ImageIO.read(new ByteArrayInputStream(png)), widthMm, heightMm, field(page, "printerName"));
                report(id, true, null);
                printed++;
                log("printed page " + field(page, "pageNo"));
            } catch (Exception ex) {
                log("page " + id + " failed: " + ex.getMessage());
                report(id, false, ex.getMessage());
            }
        }
        return printed;
    }

    private void report(String pageId, boolean ok, String error) throws IOException, InterruptedException {
        String body = "{\"printed\":%s,\"error\":%s}".formatted(ok, error == null ? "null" : quote(error));
        authed(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/labels/agent/pages/" + pageId + "/result"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    // -------------------------------------------------------------- printing ---

    /**
     * Puts one page on the printer at exactly the size the server rendered it.
     *
     * <p>No media name is asked for. The nearest standard paper to a 60 x 12 mm
     * tag is ISO A10, and naming it makes the driver print the label sideways on
     * a page a third of its width and feed tags to reach it. The Paper carries
     * the real size instead.
     */
    private void spool(BufferedImage page, double widthMm, double heightMm, String printerName)
            throws PrinterException {
        if (page == null) {
            throw new IllegalStateException("the page image could not be decoded");
        }
        PrintService service = choosePrinter(printerName);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(service);
        job.setJobName("Jewellery label");

        double widthPt = widthMm / 25.4 * 72;
        double heightPt = heightMm / 25.4 * 72;
        Paper paper = new Paper();
        paper.setSize(widthPt, heightPt);
        paper.setImageableArea(0, 0, widthPt, heightPt);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(PageFormat.PORTRAIT);

        Printable printable = (Graphics graphics, PageFormat pageFormat, int index) -> {
            if (index > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g = (Graphics2D) graphics;
            g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            g.drawImage(page, 0, 0, (int) Math.round(widthPt), (int) Math.round(heightPt), null);
            return Printable.PAGE_EXISTS;
        };
        job.setPrintable(printable, format);

        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new JobName("Jewellery label", null));
        attributes.add(new MediaPrintableArea(0f, 0f, (float) (widthMm / 25.4), (float) (heightMm / 25.4),
                MediaPrintableArea.INCH));
        attributes.add(OrientationRequested.PORTRAIT);
        job.print(attributes);
    }

    /**
     * Which printer a page goes to.
     *
     * <p>Three places it can be decided, most specific first: the printer
     * chosen in label settings and carried on the page, the one pinned in this
     * agent's configuration, and failing both, whatever Windows calls the
     * default. The middle one exists because the Windows default is a
     * machine-wide setting that anyone can change - the counter PC has an
     * office printer on it too, and a tag must not end up on A4.
     */
    private PrintService choosePrinter(String fromServer) {
        if (isNamed(fromServer)) {
            return byName(fromServer.trim(), "label settings");
        }
        if (isNamed(configuredPrinter)) {
            return byName(configuredPrinter, "this agent's configuration");
        }
        PrintService fallback = PrintServiceLookup.lookupDefaultPrintService();
        if (fallback == null) {
            throw new IllegalStateException(
                    "no printer chosen in label settings, none set in printer.name, and this PC has no default");
        }
        return fallback;
    }

    private static boolean isNamed(String name) {
        return name != null && !name.isBlank() && !"null".equals(name);
    }

    private static PrintService byName(String name, String source) {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(name)) {
                return service;
            }
        }
        throw new IllegalStateException(
                "printer \"" + name + "\" (from " + source + ") is not installed on this PC");
    }

    static List<String> localPrinters() {
        List<String> names = new ArrayList<>();
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            names.add(service.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // ------------------------------------------------------------ plumbing ---

    private HttpResponse<String> authed(HttpRequest.Builder builder)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(builder.header("Authorization", "Bearer " + token));
        if (response.statusCode() == 401) {
            throw new Unauthorised();
        }
        if (response.statusCode() >= 400) {
            throw new IOException("server said " + response.statusCode() + ": " + brief(response.body()));
        }
        return response;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return http.send(builder.header("Accept", "application/json").timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Pulls one field out of a JSON object.
     *
     * <p>Hand-rolled so the agent stays a single file with no libraries to
     * install on a shop PC. It only ever reads the handful of flat fields this
     * protocol defines; anything more would want a real parser.
     */
    static String field(String json, String name) {
        if (json == null) {
            return null;
        }
        String key = "\"" + name + "\"";
        int at = json.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        if (json.charAt(i) == '"') {
            StringBuilder value = new StringBuilder();
            for (int j = i + 1; j < json.length(); j++) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) {
                    char next = json.charAt(++j);
                    value.append(switch (next) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        default -> next;
                    });
                } else if (c == '"') {
                    return value.toString();
                } else {
                    value.append(c);
                }
            }
            return value.toString();
        }
        int end = i;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        String raw = json.substring(i, end).trim();
        return "null".equals(raw) ? null : raw;
    }

    /** Splits a flat JSON array of objects into its elements. */
    static List<String> splitObjects(String jsonArray) {
        List<String> parts = new ArrayList<>();
        if (jsonArray == null) {
            return parts;
        }
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth++ == 0) {
                    start = i;
                }
            } else if (c == '}' && --depth == 0 && start >= 0) {
                parts.add(jsonArray.substring(start, i + 1));
            }
        }
        return parts;
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String message = field(body, "message");
        String text = message != null ? message : body;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println(LocalTime.now().withNano(0) + "  " + message);
    }

    /** The access token has run out; sign in again. */
    static final class Unauthorised extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
