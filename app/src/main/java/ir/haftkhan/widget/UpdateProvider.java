package ir.haftkhan.widget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** فایل به‌روزرسانی دانلودشده را به نصب‌کننده‌ی اندروید می‌دهد (بدون نیاز به کتابخانه). */
public class UpdateProvider extends ContentProvider {
    static final String AUTH = "ir.haftkhan.widget.update";
    static final String NAME = "update.apk";

    static Uri uri() { return Uri.parse("content://" + AUTH + "/" + NAME); }

    File file() { return new File(getContext().getCacheDir(), NAME); }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri u) { return "application/vnd.android.package-archive"; }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{NAME, file().length()});
        return c;
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
