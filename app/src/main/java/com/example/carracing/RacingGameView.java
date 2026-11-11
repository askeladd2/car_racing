package com.example.carracing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class RacingGameView extends View {
    private static final float GAME_WIDTH = 900f;
    private static final float GAME_HEIGHT = 420f;
    private static final float ROAD_LEFT = 214f;
    private static final float ROAD_RIGHT = 686f;
    private static final float PLAYER_Y = 292f;
    private static final float[] LANE_CENTERS = {293f, 450f, 607f};
    private static final int BACKGROUND = Color.rgb(16, 27, 26);
    private static final int GRASS = Color.rgb(28, 57, 48);
    private static final int ROAD = Color.rgb(48, 57, 59);
    private static final int ROAD_EDGE = Color.rgb(238, 222, 184);
    private static final int PLAYER_COLOR = Color.rgb(244, 92, 75);
    private static final int[] TRAFFIC_COLORS = {
            Color.rgb(244, 185, 66), Color.rgb(75, 190, 163),
            Color.rgb(231, 231, 213), Color.rgb(105, 154, 226)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<TrafficCar> traffic = new ArrayList<>();
    private final Random random = new Random();
    private int highScore;
    private GameState state = GameState.READY;
    private int playerLane = 1;
    private int score;
    private float roadOffset;
    private float spawnTimer;
    private float speed = 300f;
    private float elapsed;
    private long lastFrameNanos;
    private float downX;
    private float downY;

    public RacingGameView(Context context) {
        super(context);
        setFocusable(true);
        highScore = context.getSharedPreferences("apex_run", Context.MODE_PRIVATE)
                .getInt("high_score", 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 0f : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.04f);
        lastFrameNanos = now;
        if (state == GameState.PLAYING) {
            update(delta);
        }

        canvas.save();
        canvas.scale(getWidth() / GAME_WIDTH, getHeight() / GAME_HEIGHT);
        drawWorld(canvas);
        if (state != GameState.PLAYING) {
            drawOverlay(canvas);
        }
        canvas.restore();
        if (state == GameState.PLAYING) {
            postInvalidateOnAnimation();
        }
    }

    private void update(float delta) {
        elapsed += delta;
        speed = Math.min(300f + elapsed * 8f, 570f);
        score = (int) (elapsed * 12f);
        roadOffset = (roadOffset + speed * delta) % 88f;
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTraffic();
            spawnTimer = Math.max(0.52f, 1.05f - elapsed * 0.008f);
        }

        RectF playerBounds = carBounds(LANE_CENTERS[playerLane], PLAYER_Y, 62f, 92f);
        Iterator<TrafficCar> iterator = traffic.iterator();
        while (iterator.hasNext()) {
            TrafficCar car = iterator.next();
            car.y += (speed + 40f) * delta;
            if (car.y > GAME_HEIGHT + 100f) {
                iterator.remove();
            } else if (RectF.intersects(playerBounds, carBounds(LANE_CENTERS[car.lane], car.y, 62f, 92f))) {
                finishGame();
                return;
            }
        }
    }

    private void spawnTraffic() {
        int lane = random.nextInt(LANE_CENTERS.length);
        if (!traffic.isEmpty() && traffic.get(traffic.size() - 1).lane == lane
                && traffic.get(traffic.size() - 1).y < 100f) {
            lane = (lane + 1 + random.nextInt(2)) % LANE_CENTERS.length;
        }
        traffic.add(new TrafficCar(lane, -70f, TRAFFIC_COLORS[random.nextInt(TRAFFIC_COLORS.length)]));
    }

    private void finishGame() {
        state = GameState.GAME_OVER;
        if (score > highScore) {
            highScore = score;
            getContext().getSharedPreferences("apex_run", Context.MODE_PRIVATE)
                    .edit().putInt("high_score", score).apply();
        }
    }

    private void startGame() {
        traffic.clear();
        playerLane = 1;
        score = 0;
        elapsed = 0f;
        speed = 300f;
        roadOffset = 0f;
        spawnTimer = 0.7f;
        lastFrameNanos = System.nanoTime();
        state = GameState.PLAYING;
        invalidate();
    }

    private void drawWorld(Canvas canvas) {
        canvas.drawColor(BACKGROUND);
        paint.setColor(GRASS);
        canvas.drawRect(0f, 0f, ROAD_LEFT, GAME_HEIGHT, paint);
        canvas.drawRect(ROAD_RIGHT, 0f, GAME_WIDTH, GAME_HEIGHT, paint);
        paint.setColor(ROAD);
        canvas.drawRect(ROAD_LEFT, 0f, ROAD_RIGHT, GAME_HEIGHT, paint);

        paint.setColor(ROAD_EDGE);
        canvas.drawRect(ROAD_LEFT + 8f, 0f, ROAD_LEFT + 13f, GAME_HEIGHT, paint);
        canvas.drawRect(ROAD_RIGHT - 13f, 0f, ROAD_RIGHT - 8f, GAME_HEIGHT, paint);
        paint.setColor(Color.rgb(130, 143, 135));
        for (int divider = 1; divider < 3; divider++) {
            float x = ROAD_LEFT + divider * (ROAD_RIGHT - ROAD_LEFT) / 3f;
            for (float y = -88f + roadOffset; y < GAME_HEIGHT; y += 88f) {
                canvas.drawRoundRect(x - 2f, y, x + 2f, y + 42f, 2f, 2f, paint);
            }
        }

        for (TrafficCar car : traffic) {
            drawCar(canvas, LANE_CENTERS[car.lane], car.y, car.color, false);
        }
        if (state != GameState.GAME_OVER) {
            drawCar(canvas, LANE_CENTERS[playerLane], PLAYER_Y, PLAYER_COLOR, true);
        }
        drawHud(canvas);
    }

    private void drawHud(Canvas canvas) {
        drawText(canvas, String.format("%05d", score), 32f, 48f, 24f, Color.WHITE, Paint.Align.LEFT);
        drawText(canvas, "DISTANCE", 33f, 70f, 11f, Color.rgb(176, 191, 181), Paint.Align.LEFT);
        drawText(canvas, "BEST  " + Math.max(highScore, score), 868f, 48f, 16f,
                Color.rgb(244, 185, 66), Paint.Align.RIGHT);
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            drawButton(canvas, 840f, 84f, 42f, 42f, "Ⅱ", Color.rgb(244, 185, 66));
        }
        drawButton(canvas, 64f, 354f, 74f, 48f, "‹", Color.rgb(244, 185, 66));
        drawButton(canvas, 836f, 354f, 74f, 48f, "›", Color.rgb(244, 185, 66));
    }

    private void drawOverlay(Canvas canvas) {
        paint.setColor(Color.argb(188, 10, 18, 19));
        canvas.drawRect(0f, 0f, GAME_WIDTH, GAME_HEIGHT, paint);
        String title;
        String subtitle;
        String button;
        if (state == GameState.READY) {
            title = "APEX RUN";
            subtitle = "DODGE TRAFFIC  /  OWN THE ROAD";
            button = "TAP TO START";
        } else if (state == GameState.PAUSED) {
            title = "PIT STOP";
            subtitle = "TAKE A BREATHER. THE ROAD CAN WAIT.";
            button = "TAP TO RESUME";
        } else {
            title = "WIPEOUT";
            subtitle = "DISTANCE " + score + "     /     BEST " + Math.max(highScore, score);
            button = "RACE AGAIN";
        }
        drawText(canvas, title, 450f, 154f, 42f, Color.WHITE, Paint.Align.CENTER);
        drawText(canvas, subtitle, 450f, 188f, 14f, Color.rgb(190, 203, 193), Paint.Align.CENTER);
        paint.setColor(Color.rgb(244, 185, 66));
        canvas.drawRoundRect(342f, 218f, 558f, 270f, 8f, 8f, paint);
        drawText(canvas, button, 450f, 251f, 16f, BACKGROUND, Paint.Align.CENTER);
        drawText(canvas, "‹   /   ›     STEER", 450f, 307f, 12f,
                Color.rgb(190, 203, 193), Paint.Align.CENTER);
    }

    private void drawCar(Canvas canvas, float centerX, float centerY, int color, boolean player) {
        float width = player ? 62f : 58f;
        float height = player ? 92f : 86f;
        rect.set(centerX - width / 2f, centerY - height / 2f,
                centerX + width / 2f, centerY + height / 2f);
        paint.setColor(Color.rgb(20, 25, 26));
        canvas.drawRoundRect(rect, 13f, 13f, paint);
        rect.inset(5f, 4f);
        paint.setColor(color);
        canvas.drawRoundRect(rect, 10f, 10f, paint);

        paint.setColor(Color.rgb(35, 54, 59));
        canvas.drawRoundRect(centerX - width * 0.32f, centerY - height * 0.27f,
                centerX + width * 0.32f, centerY - height * 0.02f, 7f, 7f, paint);
        paint.setColor(Color.rgb(44, 67, 70));
        canvas.drawRoundRect(centerX - width * 0.32f, centerY + height * 0.08f,
                centerX + width * 0.32f, centerY + height * 0.29f, 6f, 6f, paint);

        paint.setColor(Color.rgb(255, 231, 164));
        canvas.drawRoundRect(centerX - width * 0.34f, centerY - height * 0.42f,
                centerX - width * 0.13f, centerY - height * 0.37f, 2f, 2f, paint);
        canvas.drawRoundRect(centerX + width * 0.13f, centerY - height * 0.42f,
                centerX + width * 0.34f, centerY - height * 0.37f, 2f, 2f, paint);
        paint.setColor(Color.rgb(235, 89, 72));
        canvas.drawRoundRect(centerX - width * 0.34f, centerY + height * 0.38f,
                centerX - width * 0.13f, centerY + height * 0.43f, 2f, 2f, paint);
        canvas.drawRoundRect(centerX + width * 0.13f, centerY + height * 0.38f,
                centerX + width * 0.34f, centerY + height * 0.43f, 2f, 2f, paint);
    }

    private RectF carBounds(float centerX, float centerY, float width, float height) {
        return new RectF(centerX - width * 0.38f, centerY - height * 0.38f,
                centerX + width * 0.38f, centerY + height * 0.38f);
    }

    private void drawButton(Canvas canvas, float centerX, float centerY, float width, float height,
                            String label, int color) {
        paint.setColor(Color.argb(48, 255, 255, 255));
        canvas.drawRoundRect(centerX - width / 2f, centerY - height / 2f,
                centerX + width / 2f, centerY + height / 2f, 9f, 9f, paint);
        drawText(canvas, label, centerX, centerY + (label.length() == 1 ? 12f : 6f),
                label.length() == 1 ? 33f : 15f, color, Paint.Align.CENTER);
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color,
                          Paint.Align align) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX() * GAME_WIDTH / getWidth();
            downY = event.getY() * GAME_HEIGHT / getHeight();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float upX = event.getX() * GAME_WIDTH / getWidth();
            float upY = event.getY() * GAME_HEIGHT / getHeight();
            if (state == GameState.READY || state == GameState.GAME_OVER) {
                startGame();
                return true;
            }
            if (state == GameState.PAUSED) {
                state = GameState.PLAYING;
                lastFrameNanos = System.nanoTime();
                invalidate();
                return true;
            }
            if (downX > 790f && downY < 130f) {
                state = GameState.PAUSED;
                invalidate();
                return true;
            }
            if (Math.abs(upX - downX) > 35f) {
                changeLane(upX < downX ? -1 : 1);
            } else if (upX < GAME_WIDTH * 0.42f || (downX < GAME_WIDTH / 2f && downY > 300f)) {
                changeLane(-1);
            } else if (upX > GAME_WIDTH * 0.58f || downY > 300f) {
                changeLane(1);
            }
            return true;
        }
        return true;
    }

    private void changeLane(int direction) {
        playerLane = Math.max(0, Math.min(LANE_CENTERS.length - 1, playerLane + direction));
        invalidate();
    }

    private enum GameState { READY, PLAYING, PAUSED, GAME_OVER }

    private static final class TrafficCar {
        final int lane;
        final int color;
        float y;

        TrafficCar(int lane, float y, int color) {
            this.lane = lane;
            this.y = y;
            this.color = color;
        }
    }
}