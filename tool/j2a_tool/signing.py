"""Game signing keys shared by JarNix (PC) and J2Droid (Android). No passwords for the user.

The keys live in ~/.j2a/signing/identity.p12 (alias "j2a-games"). Exported/imported files use the
same fixed built-in password as J2Droid (SigningProfile.FILE_PASSWORD), so a file exported on one
side imports on the other without asking anything.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from . import paths

FILE_PASSWORD = "j2droid"  # must match android-offline/.../SigningProfile.java
ALIAS = "j2a-games"
_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def identity_file():
    return Path(os.environ.get("J2A_SIGNING_FILE") or
                (Path.home() / ".j2a" / "signing" / "identity.p12")).expanduser()


def password():
    return os.environ.get("J2A_SIGNING_PASSWORD") or FILE_PASSWORD


def _keytool(*args):
    tool = paths.java_bin("keytool")
    if not tool:
        raise RuntimeError("No encuentro keytool. Instala Android Studio o un JDK y configura JAVA_HOME.")
    # legacy PKCS12 algorithms: readable by every Android version J2Droid supports
    cmd = [tool, "-J-Dkeystore.pkcs12.legacy", "-J-Duser.language=en", "-noprompt"] + list(args)
    done = subprocess.run(cmd, capture_output=True, text=True, errors="replace", creationflags=_NO_WINDOW)
    if done.returncode:
        raise RuntimeError((done.stdout + done.stderr).strip() or "keytool falló")
    return done.stdout


def _first_alias(store, secret):
    out = _keytool("-list", "-keystore", str(store), "-storetype", "PKCS12", "-storepass", secret)
    names = [line.split(",")[0].strip() for line in out.splitlines() if "PrivateKeyEntry" in line]
    if ALIAS in names:
        return ALIAS
    if not names:
        raise RuntimeError("El archivo no contiene llaves de firma.")
    return names[0]


def _convert(src, src_type, src_pass, src_alias, dest):
    """Copies one key into dest as PKCS12 / alias j2a-games / built-in password."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".tmp.p12")
    if tmp.exists():
        tmp.unlink()
    _keytool("-importkeystore", "-srckeystore", str(src), "-srcstoretype", src_type,
             "-srcstorepass", src_pass, "-srcalias", src_alias, "-srckeypass", src_pass,
             "-destkeystore", str(tmp), "-deststoretype", "PKCS12", "-destalias", ALIAS,
             "-deststorepass", FILE_PASSWORD, "-destkeypass", FILE_PASSWORD)
    os.replace(str(tmp), str(dest))


def ensure_identity(log=print):
    """Returns the keys file, creating it the first time.

    First choice is the Android Studio debug key, so games already installed from earlier
    JarNix builds keep updating; otherwise a new key is generated."""
    target = identity_file()
    if target.is_file():
        return target
    debug = Path.home() / ".android" / "debug.keystore"
    if debug.is_file():
        try:
            store_type = "PKCS12" if debug.read_bytes()[:1] == b"\x30" else "JKS"
            _convert(debug, store_type, "android", "androiddebugkey", target)
            log("Llaves creadas a partir de tu clave de Android Studio (tus juegos ya instalados se siguen actualizando).")
            return target
        except RuntimeError:
            pass
    target.parent.mkdir(parents=True, exist_ok=True)
    _keytool("-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10950",
             "-dname", "CN=J2A Game Signing", "-keystore", str(target), "-storetype", "PKCS12",
             "-storepass", FILE_PASSWORD, "-keypass", FILE_PASSWORD)
    log("Llaves nuevas creadas en %s" % target)
    return target


def export_to(dest, log=print):
    shutil.copyfile(str(ensure_identity(log)), str(dest))


def needs_password(src):
    """True for old backups that were exported with a personal password."""
    try:
        _first_alias(src, FILE_PASSWORD)
        return False
    except RuntimeError as error:
        if "password" in str(error).lower():
            return True
        raise


def import_from(src, old_password=None):
    """Replaces the PC keys with a file exported by J2Droid or JarNix."""
    secret = old_password or FILE_PASSWORD
    alias = _first_alias(src, secret)
    target = identity_file()
    with tempfile.TemporaryDirectory() as tmp:
        staged = Path(tmp) / "identity.p12"
        _convert(Path(src), "PKCS12", secret, alias, staged)
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.is_file():
            shutil.copyfile(str(target), str(target.with_suffix(".anterior.p12")))
        shutil.copyfile(str(staged), str(target))
    return target
