package net.nologin.meep.tbv;

import android.util.Log;
import android.util.Pair;

/**
 * Represents the state of the tiles to be rendered in the TileBitmapView.
 *
 * TiledBitmapView maintains an instance of this class, and updates the content on lifecycle and
 * user interaction events (Eg, start, scroll etc).  The state contained in this mutable class should
 * be stored and read in a thread-safe atomic manner.  This means the rendering thread should take
 * snapshots of the 'state' that it needs to render in a synchronized manner, as the the UI thread
 * may have since updated the state.
 *
 */
public final class ViewState {

    public final int tileWidth;

    public final int tilesHoriz, tilesVert;

    public final int screenW, screenH;

    private final Integer[] bounds;

    private int surfaceOffsetX, surfaceOffsetY;
    private int canvasOffsetX, canvasOffsetY;
    private TileRange visibleTileIdRange;
    private float zoomFactor = 1.0f; // ScaleListener sets this from 0.1 to 5.0

    private Snapshot snapshot;

    class Snapshot {

        public int surfaceOffsetX, surfaceOffsetY, canvasOffsetX, canvasOffsetY;
        public TileRange visibleTileIdRange;
        public float zoomFactor;

    }

    public ViewState(int screenW, int screenH, int tileWidth, Integer[] bounds) {

        this.screenW = screenW;
        this.screenH = screenH;
        this.tileWidth = tileWidth;

        if (bounds != null && bounds.length != 4) {
            Log.w(Utils.LOG_TAG, "Provider provided " + bounds.length + " elements, must be 4 - Ignoring.");
            bounds = null;
        }
        this.bounds = bounds;

        tilesHoriz = calculateNumTiles(screenW);
        tilesVert = calculateNumTiles(screenH);

    }

    public synchronized Snapshot createSnapshot() {

        if (snapshot == null) {
            snapshot = new Snapshot();
        }
        snapshot.visibleTileIdRange = this.visibleTileIdRange;
        snapshot.surfaceOffsetX = this.surfaceOffsetX;
        snapshot.surfaceOffsetY = this.surfaceOffsetY;
        snapshot.canvasOffsetX = this.canvasOffsetX;
        snapshot.canvasOffsetY = this.canvasOffsetY;
        snapshot.zoomFactor = this.zoomFactor;

        return snapshot;
    }

    public synchronized TileRange getVisibleTileRange(){
        return visibleTileIdRange;
    }

    public synchronized boolean applySurfaceOffsetRelative(int relOffsetX, int relOffsetY) {

        return applySurfaceOffset(surfaceOffsetX + relOffsetX, surfaceOffsetY + relOffsetY);
    }

    public synchronized boolean applySurfaceOffset(int offsetX, int offsetY) {

        Pair<Integer, Integer> range_horiz = calculateTileIDRange(offsetX, tilesHoriz);
        Pair<Integer, Integer> range_vert = calculateTileIDRange(offsetY, tilesVert);

        if (bounds != null && visibleTileIdRange != null) {

            /* Important to check horizontal and vertical bounds independently, so that diagonal swipes that
               hit a boundary continue to update the scroll.  (Eg, if I'm at the top boundary, and swipe up-left,
               we still want the left part of that scroll to be obeyed.
            */
            if ((bounds[0] != null && range_horiz.first < bounds[0])  // left
                    || (bounds[2] != null && range_horiz.second > bounds[2])) {  // right
                // Horizontal check fails, keep existing values
                range_horiz = new Pair<Integer, Integer>(visibleTileIdRange.left, visibleTileIdRange.right);
                offsetX = surfaceOffsetX;
            }

            if ((bounds[1] != null && range_vert.first < bounds[1]) // top
                    || (bounds[3] != null && range_vert.second > bounds[3])) { // bottom
                // Vertical check fails, keep existing values
                range_vert = new Pair<Integer, Integer>(visibleTileIdRange.top, visibleTileIdRange.bottom);
                offsetY = surfaceOffsetY;
            }

        }

        TileRange newRange = new TileRange(range_horiz.first, range_vert.first, range_horiz.second, range_vert.second);
        boolean rangeHasChanged = (visibleTileIdRange == null || !newRange.equals(visibleTileIdRange));
        visibleTileIdRange = newRange;


        surfaceOffsetX = offsetX;
        surfaceOffsetY = offsetY;

        canvasOffsetX = surfaceOffsetX % tileWidth;
        canvasOffsetY = surfaceOffsetY % tileWidth;

        // in the case we're offset to the right, we need to start rendering 'back' a tile (the longer tile range
        // handles the case of left offset)
        if (canvasOffsetX > 0) {
            canvasOffsetX -= tileWidth;
        }
        if (canvasOffsetY > 0) {
            canvasOffsetY -= tileWidth;
        }

        return rangeHasChanged;

    }


    private Pair<Integer, Integer> calculateTileIDRange(int coordPx, int numTiles) {

        int startTileId = -(coordPx / tileWidth);
        //int startTileId = (-numTiles / 2) - (offset / tileWidth);

        // positive offset means one tile before (negative handled by numTiles)
        if (coordPx % tileWidth > 0) {
            startTileId--;
        }

        int endTileId = startTileId + numTiles - 1;

        return new Pair<Integer, Integer>(startTileId, endTileId);
    }


    /**
     * @return The largest possible number of tiles needed to render a row/column
     *         for the given tile size.  (eg, if two tiles fit perfectly, we'll still need
     *         3 for then the user scrolls slightly off to one side).
     */
    private int calculateNumTiles(int availablePx) {

        /* The + 1 ist to cover scrolling (eg, scroll left a bit, and part of a
         * new tile will appear on the right, but we still need the left tile */
        int num = (availablePx / tileWidth) + 1;

        /* An additional tile if the int division above floored */
        num += (availablePx % tileWidth == 0 ? 0 : 1);

        return num;
    }


    public synchronized float updateZoomFactor(float zoomFactor) {

        this.zoomFactor *= zoomFactor;

        // Don't let the object get too small or too large.
        zoomFactor = Math.max(1.0f, Math.min(zoomFactor, 5.0f));

        return zoomFactor;

    }


}
