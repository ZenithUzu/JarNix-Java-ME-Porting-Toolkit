package j2a.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * J2Droid: one simple screen. Importar JAR → Crear APK → Instalar / Guardar, plus
 * Exportar / Importar llaves (no passwords). Colors follow Material You on Android 12+.
 */
public final class MainActivity extends Activity {
    private static final int PICK_JAR = 1, PICK_JAD = 2, SAVE_APK = 3, EXPORT_KEYS = 4, IMPORT_KEYS = 5;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Runnable poll = new Runnable() { public void run() { refresh(); ui.postDelayed(this, 700); } };
    private boolean copying, showLog;
    private String previousLog = "";
    private String legacyPassword;

    // Palette: Material You (dynamic) on Android 12+, otherwise the J2Droid icon colors.
    private int BG, CARD, TONAL, TEXT, MUTED, ACCENT, ON_ACCENT, ON_TONAL;

    private LinearLayout content;
    private TextView jarTitle, jarSubtitle, jadLine, status, log, logToggle;
    private TextView convert, install, save;
    private LinearLayout resultRow;
    private ProgressBar progress;

    // ------------------------------------------------------------------ look
    private boolean night() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    private void palette() {
        boolean dark = night();
        if (Build.VERSION.SDK_INT >= 31) {
            BG = getColor(dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_10);
            CARD = getColor(dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50);
            TONAL = getColor(dark ? android.R.color.system_accent2_700 : android.R.color.system_accent2_100);
            ON_TONAL = getColor(dark ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900);
            TEXT = getColor(dark ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900);
            MUTED = getColor(dark ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700);
            ACCENT = getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
            ON_ACCENT = getColor(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        } else if (dark) {
            BG = 0xff15131c; CARD = 0xff221f2b; TONAL = 0xff3a3456; ON_TONAL = 0xffe6deff;
            TEXT = 0xfff3effa; MUTED = 0xffc9c3d6; ACCENT = 0xffc6b8ff; ON_ACCENT = 0xff2e1f73;
        } else {
            BG = 0xfffcf9ff; CARD = 0xfff2eef9; TONAL = 0xffe5defc; ON_TONAL = 0xff221a4a;
            TEXT = 0xff1c1a22; MUTED = 0xff5d5a68; ACCENT = 0xff6750d8; ON_ACCENT = 0xffffffff;
        }
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    private TextView button(String label, int bg, int fg, View.OnClickListener click) {
        TextView b = text(label, 16, fg, true);
        b.setGravity(Gravity.CENTER); b.setBackground(round(bg, 28)); b.setMinHeight(dp(54));
        b.setPadding(dp(16), 0, dp(16), 0); b.setOnClickListener(click); b.setClickable(true); b.setFocusable(true);
        return b;
    }
    private LinearLayout tile(String title, String subtitle, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16)); l.setBackground(round(CARD, 24));
        l.addView(text(title, 16, TEXT, true));
        if (subtitle != null) l.addView(text(subtitle, 13, MUTED, false));
        l.setOnClickListener(click); l.setClickable(true); l.setFocusable(true);
        return l;
    }
    private void add(View v, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); content.addView(v, p);
    }

    // ------------------------------------------------------------------ screen
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        palette();
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        if (!night()) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        SharedPreferences saved = getSharedPreferences(BuildService.STATE, 0);
        if (saved.getBoolean("busy", false) && !BuildService.isRunning())
            saved.edit().putBoolean("busy", false).putString("log", saved.getString("log", "") + "\nLa conversión anterior se interrumpió.\n").apply();

        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(28)); scroll.addView(content);
        setContentView(scroll);

        // Header: app icon + name
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(14), 0, 0, 0);
        names.addView(text("J2Droid", 26, TEXT, true));
        String version = ""; try { version = " · v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        names.addView(text("Juegos Java ME a APK" + version, 13, MUTED, false));
        header.addView(names);
        add(header, 0);

        // 1. Importar JAR
        LinearLayout jar = tile("Importar JAR", "Toca para elegir un juego .jar", v -> pick(PICK_JAR));
        jarTitle = (TextView) jar.getChildAt(0); jarSubtitle = (TextView) jar.getChildAt(1);
        add(jar, 26);
        jadLine = text("", 13, ACCENT, true); jadLine.setPadding(dp(18), dp(10), dp(18), dp(4));
        jadLine.setOnClickListener(v -> {
            if (getPreferences(0).getString("jad", "").isEmpty()) pick(PICK_JAD);
            else { getPreferences(0).edit().remove("jad").apply(); refresh(); }
        });
        add(jadLine, 0);

        // 2. Crear APK
        convert = button("Crear APK", ACCENT, ON_ACCENT, v -> startBuild());
        add(convert, 14);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        add(progress, 12);
        status = text("", 14, MUTED, false); status.setGravity(Gravity.CENTER_HORIZONTAL);
        add(status, 6);

        // 3. Resultado: Instalar / Guardar
        resultRow = new LinearLayout(this);
        install = button("Instalar", ACCENT, ON_ACCENT, v -> installApk());
        save = button("Guardar", TONAL, ON_TONAL, v -> saveApk());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1); right.leftMargin = dp(6);
        resultRow.addView(install, left); resultRow.addView(save, right);
        add(resultRow, 10);

        // 4. Llaves
        add(text("Llaves de firma", 15, ACCENT, true), 30);
        add(text("Expórtalas antes de borrar o reinstalar J2Droid. Al importarlas, tus juegos se podrán actualizar sin perder partidas. También sirven en JarNix (PC).", 13, MUTED, false), 4);
        LinearLayout keys = new LinearLayout(this);
        LinearLayout export = tile("Exportar llaves", "Guardar archivo .p12", v -> exportKeys());
        LinearLayout imp = tile("Importar llaves", "Usar un archivo .p12", v -> importKeys());
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, -1, 1); kl.rightMargin = dp(6);
        LinearLayout.LayoutParams kr = new LinearLayout.LayoutParams(0, -1, 1); kr.leftMargin = dp(6);
        keys.addView(export, kl); keys.addView(imp, kr);
        add(keys, 12);

        // Detalles (registro)
        logToggle = text("Ver detalles", 13, ACCENT, true); logToggle.setPadding(0, dp(8), 0, dp(8));
        logToggle.setOnClickListener(v -> { showLog = !showLog; refresh(); });
        add(logToggle, 18);
        log = text("", 12, MUTED, false); log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true);
        log.setBackground(round(CARD, 16)); log.setPadding(dp(14), dp(12), dp(14), dp(12));
        add(log, 4);

        refresh();
    }
    @Override protected void onResume() { super.onResume(); ui.post(poll); }
    @Override protected void onPause() { ui.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { ui.removeCallbacks(poll); files.shutdown(); super.onDestroy(); }

    private void refresh() {
        if (convert == null) return;
        SharedPreferences state = getSharedPreferences(BuildService.STATE, 0), prefs = getPreferences(0);
        boolean busy = copying || state.getBoolean("busy", false);
        String jar = prefs.getString("jar", ""), jad = prefs.getString("jad", ""), result = state.getString("result", "");
        boolean ready = !busy && !result.isEmpty() && new File(result).isFile();
        jarTitle.setText(jar.isEmpty() ? "Importar JAR" : prefs.getString("name", "Juego.jar"));
        jarSubtitle.setText(jar.isEmpty() ? "Toca para elegir un juego .jar" : "Toca para cambiar de juego");
        jadLine.setVisibility(jar.isEmpty() ? View.GONE : View.VISIBLE);
        jadLine.setText(jad.isEmpty() ? "+ Añadir JAD (opcional)" : "JAD añadido · Quitar");
        convert.setEnabled(!busy && !jar.isEmpty()); convert.setAlpha(convert.isEnabled() ? 1f : 0.45f);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        resultRow.setVisibility(ready ? View.VISIBLE : View.GONE);
        status.setText(busy ? "Creando APK…" : ready ? "APK lista" : "");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        String lines = state.getString("log", "");
        if (!lines.equals(previousLog)) { log.setText(lines); previousLog = lines; }
        logToggle.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);
        logToggle.setText(showLog ? "Ocultar detalles" : "Ver detalles");
        log.setVisibility(showLog && !lines.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ actions
    private void pick(int request) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), request);
    }
    private void startBuild() {
        SharedPreferences prefs = getPreferences(0); String jar = prefs.getString("jar", null), jad = prefs.getString("jad", null);
        if (jar == null) return;
        Intent intent = new Intent(this, BuildService.class).putExtra("jar", jar).putExtra("name", prefs.getString("name", "Juego.jar"));
        if (jad != null) intent.putExtra("jad", jad);
        SharedPreferences state = getSharedPreferences(BuildService.STATE, 0);
        state.edit().putBoolean("busy", true).putString("result", "").putString("log", "").apply(); refresh();
        try { startForegroundService(intent); }
        catch (RuntimeException error) { state.edit().putBoolean("busy", false).putString("log", "No se pudo iniciar la conversión: " + error).apply(); refresh(); }
    }
    private boolean idle() {
        if (!BuildService.isRunning()) return true;
        Toast.makeText(this, "Espera a que termine la conversión", Toast.LENGTH_LONG).show(); return false;
    }
    private void exportKeys() {
        if (!idle()) return;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-pkcs12")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "J2Droid-llaves.p12"), EXPORT_KEYS);
    }
    private void importKeys() {
        if (!idle()) return;
        if (!new SigningProfile(this).exists()) { pick(IMPORT_KEYS); return; }
        new AlertDialog.Builder(this).setTitle("¿Reemplazar llaves?")
                .setMessage("Los juegos que ya creaste con las llaves actuales solo podrán actualizarse si vuelves a importarlas.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Importar", (d, w) -> pick(IMPORT_KEYS)).show();
    }
    /** Only for old backups made by earlier versions that asked for a personal password. */
    private void askLegacyPassword(Uri uri) {
        EditText field = new EditText(this); field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint("Contraseña del respaldo antiguo");
        new AlertDialog.Builder(this).setTitle("Respaldo antiguo")
                .setMessage("Este archivo se exportó con una versión anterior que usaba contraseña. Escríbela una vez; después exporta de nuevo y ya no la necesitarás.")
                .setView(field).setNegativeButton("Cancelar", null)
                .setPositiveButton("Importar", (d, w) -> { legacyPassword = field.getText().toString(); handle(IMPORT_KEYS, uri); }).show();
    }
    private void installApk() {
        File apk = new File(getSharedPreferences(BuildService.STATE, 0).getString("result", ""));
        if (!apk.isFile()) { Toast.makeText(this, "Primero crea una APK", Toast.LENGTH_LONG).show(); return; }
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("Permitir instalación")
                    .setMessage("Android pide autorizar a J2Droid para instalar las APK que crea.")
                    .setNegativeButton("Cancelar", null).setPositiveButton("Abrir ajustes", (d, w) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())))).show();
            return;
        }
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("No se pudo instalar").setMessage(String.valueOf(e.getMessage())).setPositiveButton("Cerrar", null).show();
        }
    }
    private void saveApk() {
        String result = getSharedPreferences(BuildService.STATE, 0).getString("result", "");
        if (result.isEmpty()) return;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.android.package-archive")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, new File(result).getName()), SAVE_APK);
    }

    @Override protected void onActivityResult(int request, int code, Intent intent) {
        super.onActivityResult(request, code, intent);
        if (code == RESULT_OK && intent != null && intent.getData() != null) handle(request, intent.getData());
    }
    private void toast(String message) { ui.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }
    private void handle(int request, Uri uri) {
        copying = true; refresh();
        String password = legacyPassword; legacyPassword = null;
        files.execute(() -> {
            try {
                if (request == EXPORT_KEYS) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) throw new IOException("No se pudo guardar el archivo");
                        new SigningProfile(this).exportTo(out);
                    }
                    toast("Llaves exportadas");
                } else if (request == IMPORT_KEYS) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IOException("No se pudo leer el archivo");
                        new SigningProfile(this).importFrom(in, password);
                        toast("Llaves importadas");
                    } catch (SigningProfile.NeedsPassword old) {
                        ui.post(() -> askLegacyPassword(uri));
                    }
                } else if (request == SAVE_APK) {
                    String result = getSharedPreferences(BuildService.STATE, 0).getString("result", "");
                    try (InputStream in = new FileInputStream(result); OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) throw new IOException("No se pudo abrir el destino");
                        OfflineBuilder.copy(in, out);
                    }
                    toast("APK guardada");
                } else {
                    String name = "Juego.jar";
                    try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (c != null && c.moveToFirst()) name = c.getString(0);
                    }
                    File target = new File(getFilesDir(), request == PICK_JAR ? "input.jar" : "input.jad"), temporary = new File(getFilesDir(), "input.tmp");
                    try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temporary)) {
                        if (in == null) throw new IOException("No se pudo leer el archivo");
                        out.write(Archives.read(in, request == PICK_JAR ? 64 * 1024 * 1024 : 1024 * 1024));
                    }
                    java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    SharedPreferences.Editor edit = getPreferences(0).edit().putString(request == PICK_JAR ? "jar" : "jad", target.getAbsolutePath());
                    if (request == PICK_JAR) {
                        edit.putString("name", name).remove("jad");
                        getSharedPreferences(BuildService.STATE, 0).edit().putString("result", "").putString("log", "").apply();
                    }
                    edit.apply();
                }
            } catch (Exception error) {
                toast(error.getMessage() != null ? error.getMessage() : error.toString());
            } finally {
                copying = false; ui.post(this::refresh);
            }
        });
    }
}
