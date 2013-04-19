package net.nologin.meep.tbv.demo;

import android.content.Context;
import android.graphics.*;
import net.nologin.meep.tbv.GridAnchor;
import net.nologin.meep.tbv.Tile;
import net.nologin.meep.tbv.TileProvider;
import net.nologin.meep.tbv.TileRange;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleTileProvider implements TileProvider {

    private Context ctx;
    private static final String DEBUG_SUMMARY_FMT = "STProv[cache=%d,queue=%d]";

    // keep a cache of tiles we've already seen or are currently in the process of rendering
     private final Map<String,SimpleTile> tileCache;

    private final Map<String,Bitmap> resCache;

    Paint tileTextPaint;

    // a queue of tiles to render in the background (in case of slow processing), must be multi-thread-friendly
    private final List<SimpleTile> renderQueue;

    public SimpleTileProvider(Context ctx){

        this.ctx = ctx;

        tileCache = new ConcurrentHashMap<String,SimpleTile>();
        renderQueue = Collections.synchronizedList(new LinkedList<SimpleTile>());

        resCache = new HashMap<String, Bitmap>();

        tileTextPaint = new Paint();
        tileTextPaint.setColor(Color.BLUE);
        tileTextPaint.setTextSize(80);
        tileTextPaint.setAntiAlias(true);
        tileTextPaint.setTextAlign(Paint.Align.LEFT);

    }


    @Override
    public int getTileWidthPixels() {
        return SimpleTile.TILE_SIZE;
    }

    @Override
    public Tile getTile(int x, int y) {

        /* don't render the tile here, that could hold up the UI.  The call to notifyTileIDRangeChange() will
         * let us add the required tiles to a queue that we can render in the bg in generateNextTile(). */
        return tileCache.get(x + "_" + y);

    }

    @Override
    public GridAnchor getGridAnchor() {
        return GridAnchor.SE;
    }

    @Override
    public void generateNextTile(TileRange visible) {

        SimpleTile t;

        // pop the next item to render off our queue
        synchronized (renderQueue){
            if(renderQueue.isEmpty()){
                return;
            }
            t = renderQueue.remove(0);
        }

        // anything to render?
        if(t == null || t.getBmpData() != null){
            return; // nothing to render
        }


        // okay, build the contents of the tile.  Doesn't matter if it's slow, we're in a bg thread
        // plus this is just a demo so I won't tweak for performance here - but you should :)

        // have tileable resources and rows and cols 1-5, loop our indexes to match
        // http://stackoverflow.com/a/4412200/276183 for handling negative mod in java
        int col = (t.xId % 5 + 5) % 5 + 1;
        int row = (t.yId % 5 + 5) % 5 + 1;
        String resName = "sr" + row + "c" + col;

        Bitmap bmp = resCache.get(resName);

        if(bmp == null){

            int resID = ctx.getResources().getIdentifier(resName,"drawable", ctx.getPackageName());

            bmp = BitmapFactory.decodeResource(ctx.getResources(),resID);

            resCache.put(resName,bmp);

        }


        //Canvas c = new Canvas(bmp);
        //c.drawText(t.xId+","+t.yId, 30, 80, tileTextPaint);

        t.setBmpData(bmp);

        // put it in the cache for the UI thread to find via getTile()
        tileCache.put(t.xId + "_" + t.yId, t);


    }

//    @Override
//    public TileRange getTileIndexBounds() {
//        return new TileRange(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
//    }

    public boolean scrollPastBoundaries(){
        return false;
    }

    @Override
    public void notifyTileIDRangeChange(TileRange newRange) {

        // clear out the bitmaps of any off-screen cached tiles, so we don't gobble ram
        Collection<SimpleTile> entries = tileCache.values();
        for(SimpleTile t : entries){
            if(t.getBmpData() != null && !newRange.contains(t)){
               t.setBmpData(null);
            }

        }

        // find out what visible tiles are missing bmp data, we'll add that to the queue
        synchronized (renderQueue){
            // wipe the render queue
            renderQueue.clear();

            for(int y = newRange.top; y <= newRange.bottom; y++) {
                for(int x = newRange.left; x <= newRange.right; x++) {

                    SimpleTile t = (SimpleTile)getTile(x, y);
                    if(t == null || t.getBmpData() == null){
                        renderQueue.add(new SimpleTile(x,y));
                    }

                }
            }
        }


    }

    @Override
    public void notifyZoomFactorChangeTEMP(float newZoom) {

        // TODO:

    }

    @Override
    public String getDebugSummary(){
        int cache = tileCache == null ? 0 : tileCache.size();
        int queue = renderQueue == null ? 0 : renderQueue.size();

        return String.format(DEBUG_SUMMARY_FMT, cache, queue);
    }
}
