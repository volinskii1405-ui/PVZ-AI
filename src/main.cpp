// PVZ-AI — клон "Растения против зомби" на C++/SDL2.
// Валюта: Семечки. Первое растение: Маслострел.

#include <SDL.h>
#include <SDL_ttf.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <optional>
#include <random>
#include <string>
#include <vector>

namespace {

constexpr int COLS = 9;
constexpr int ROWS = 5;
constexpr int CELL = 90;
constexpr int FIELD_X = 25;
constexpr int FIELD_Y = 110;
constexpr int FIELD_W = COLS * CELL;
constexpr int FIELD_H = ROWS * CELL;
constexpr int WIN_W = FIELD_X * 2 + FIELD_W;
constexpr int WIN_H = FIELD_Y + FIELD_H + 20;
constexpr int WIN_KILLS = 15;

enum class PlantType { OilShooter, Sunflower };
enum class ZombieType { Basic };

struct PlantDef {
  std::string name;
  std::string shortLabel;
  int cost;
  int hp;
  SDL_Color color;
  Uint32 actionRateMs;  // скорострельность или скорость производства
  int damage;           // урон снаряда (для стрелка)
  int produceAmount;    // количество семечек за раз (для подсолнуха)
};

struct ZombieDef {
  std::string name;
  int hp;
  float speed;  // px / ms
  int biteDamage;
  Uint32 attackRateMs;
  SDL_Color color;
};

const PlantDef& plantDef(PlantType t) {
  static const PlantDef oil{"Маслострел", "МС", 100, 100,
                             SDL_Color{235, 193, 60, 255}, 1500, 20, 0};
  static const PlantDef sun{"Подсолнух", "ПД", 50, 80,
                             SDL_Color{255, 165, 40, 255}, 12000, 0, 25};
  return t == PlantType::OilShooter ? oil : sun;
}

const ZombieDef& zombieDef(ZombieType) {
  static const ZombieDef basic{"Зомби", 100, 0.028f, 34, 700,
                                SDL_Color{110, 130, 100, 255}};
  return basic;
}

struct Plant {
  PlantType type;
  int hp;
  int maxHp;
  int row;
  int col;
  Uint32 lastActionMs;
};

struct Zombie {
  ZombieType type;
  int row;
  float x;  // относительно левого края поля
  int hp;
  int maxHp;
  Uint32 lastAttackMs;
};

struct Projectile {
  float x, y;
  int row;
  int damage;
  float speed;
};

struct FallingSeed {
  float x, y, targetY;
  int amount;
  bool falling;
  Uint32 bornAt;
};

SDL_Rect seedCounterRect{15, 15, 170, 80};
SDL_Rect oilCardRect{200, 15, 110, 80};
SDL_Rect sunCardRect{320, 15, 110, 80};
SDL_Rect restartBtnRect{WIN_W / 2 - 90, WIN_H / 2 + 30, 180, 46};

void drawFilledCircle(SDL_Renderer* r, int cx, int cy, int radius,
                       SDL_Color c) {
  SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
  for (int dy = -radius; dy <= radius; ++dy) {
    int dx = static_cast<int>(
        std::sqrt(static_cast<double>(radius * radius - dy * dy)));
    SDL_RenderDrawLine(r, cx - dx, cy + dy, cx + dx, cy + dy);
  }
}

void drawRect(SDL_Renderer* r, SDL_Rect rect, SDL_Color c, bool filled = true) {
  SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
  if (filled)
    SDL_RenderFillRect(r, &rect);
  else
    SDL_RenderDrawRect(r, &rect);
}

class TextRenderer {
 public:
  TextRenderer(SDL_Renderer* renderer, TTF_Font* font)
      : renderer_(renderer), font_(font) {}

  void draw(const std::string& text, int x, int y, SDL_Color color,
            bool centered = false) {
    if (text.empty()) return;
    SDL_Surface* surf =
        TTF_RenderUTF8_Blended(font_, text.c_str(), color);
    if (!surf) return;
    SDL_Texture* tex = SDL_CreateTextureFromSurface(renderer_, surf);
    SDL_Rect dst{x, y, surf->w, surf->h};
    if (centered) {
      dst.x -= surf->w / 2;
      dst.y -= surf->h / 2;
    }
    SDL_RenderCopy(renderer_, tex, nullptr, &dst);
    SDL_DestroyTexture(tex);
    SDL_FreeSurface(surf);
  }

  SDL_Point measure(const std::string& text) {
    int w = 0, h = 0;
    TTF_SizeUTF8(font_, text.c_str(), &w, &h);
    return {w, h};
  }

 private:
  SDL_Renderer* renderer_;
  TTF_Font* font_;
};

class Game {
 public:
  Game() { reset(); }

  void reset() {
    seeds = 50;
    selectedPlant.reset();
    for (auto& row : grid)
      for (auto& cell : row) cell.reset();
    zombies.clear();
    projectiles.clear();
    fallingSeeds.clear();
    kills = 0;
    wave = 1;
    zombieSpawnIntervalMs = 5000;
    seedDropIntervalMs = 8000;
    running = true;
    won = false;
    Uint32 now = SDL_GetTicks();
    lastZombieSpawn = now;
    lastSeedDrop = now;
  }

  void handleClick(int mx, int my) {
    if (!running) {
      if (pointInRect(mx, my, restartBtnRect)) reset();
      return;
    }

    // Сбор упавших/произведённых семечек.
    for (auto it = fallingSeeds.begin(); it != fallingSeeds.end(); ++it) {
      float sx = FIELD_X + it->x;
      float sy = FIELD_Y + it->y;
      float dx = mx - sx, dy = my - sy;
      if (std::sqrt(dx * dx + dy * dy) < 24) {
        seeds += it->amount;
        fallingSeeds.erase(it);
        return;
      }
    }

    if (pointInRect(mx, my, oilCardRect)) {
      toggleSelection(PlantType::OilShooter);
      return;
    }
    if (pointInRect(mx, my, sunCardRect)) {
      toggleSelection(PlantType::Sunflower);
      return;
    }

    if (!selectedPlant.has_value()) return;
    int col = (mx - FIELD_X) / CELL;
    int row = (my - FIELD_Y) / CELL;
    if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return;
    if (mx < FIELD_X || my < FIELD_Y) return;
    if (grid[row][col].has_value()) return;

    const PlantDef& def = plantDef(*selectedPlant);
    if (seeds < def.cost) return;

    seeds -= def.cost;
    grid[row][col] = Plant{*selectedPlant, def.hp, def.hp, row, col,
                            SDL_GetTicks()};
    selectedPlant.reset();
  }

  void update(Uint32 now, Uint32 dt) {
    if (!running) return;

    if (now - lastZombieSpawn > zombieSpawnIntervalMs) {
      spawnZombie();
      lastZombieSpawn = now;
      zombieSpawnIntervalMs =
          std::max<Uint32>(2200, zombieSpawnIntervalMs - 60);
    }

    if (now - lastSeedDrop > seedDropIntervalMs) {
      spawnFallingSeedFromSky();
      lastSeedDrop = now;
    }

    for (int row = 0; row < ROWS; ++row) {
      for (int col = 0; col < COLS; ++col) {
        auto& cell = grid[row][col];
        if (!cell.has_value()) continue;
        Plant& plant = *cell;
        const PlantDef& def = plantDef(plant.type);

        if (plant.type == PlantType::OilShooter) {
          bool hasTarget = std::any_of(
              zombies.begin(), zombies.end(), [&](const Zombie& z) {
                return z.row == row && z.x > static_cast<float>(col * CELL);
              });
          if (hasTarget && now - plant.lastActionMs > def.actionRateMs) {
            float py = row * CELL + CELL / 2.0f;
            float px = col * CELL + CELL / 2.0f + 20.0f;
            projectiles.push_back(Projectile{px, py, row, def.damage, 0.4f});
            plant.lastActionMs = now;
          }
        } else if (plant.type == PlantType::Sunflower) {
          if (now - plant.lastActionMs > def.actionRateMs) {
            float cx = col * CELL + CELL / 2.0f;
            float cy = row * CELL + CELL / 2.0f - 20.0f;
            fallingSeeds.push_back(
                FallingSeed{cx, cy, cy, def.produceAmount, false, now});
            plant.lastActionMs = now;
          }
        }
      }
    }

    for (auto it = projectiles.begin(); it != projectiles.end();) {
      it->x += it->speed * static_cast<float>(dt);
      bool hit = false;
      for (auto& z : zombies) {
        if (z.row != it->row) continue;
        if (std::fabs(z.x - it->x) < 26.0f) {
          z.hp -= it->damage;
          hit = true;
          break;
        }
      }
      if (hit || it->x > FIELD_W) {
        it = projectiles.erase(it);
      } else {
        ++it;
      }
    }

    for (auto it = zombies.begin(); it != zombies.end();) {
      const ZombieDef& def = zombieDef(it->type);
      if (it->hp <= 0) {
        it = zombies.erase(it);
        ++kills;
        continue;
      }

      int col = static_cast<int>(it->x) / CELL;
      Plant* plant = (col >= 0 && col < COLS && grid[it->row][col].has_value())
                         ? &(*grid[it->row][col])
                         : nullptr;
      bool blocked = plant && (it->x - col * CELL) < CELL * 0.6f &&
                     it->x > static_cast<float>(col * CELL);

      if (blocked) {
        if (now - it->lastAttackMs > def.attackRateMs) {
          plant->hp -= def.biteDamage;
          it->lastAttackMs = now;
          if (plant->hp <= 0) grid[it->row][col].reset();
        }
      } else {
        it->x -= def.speed * static_cast<float>(dt);
      }

      if (it->x <= -10.0f) {
        running = false;
        won = false;
        return;
      }
      ++it;
    }

    for (auto it = fallingSeeds.begin(); it != fallingSeeds.end();) {
      if (it->falling && it->y < it->targetY) {
        it->y += 0.09f * static_cast<float>(dt);
      }
      if (now - it->bornAt > 9000) {
        it = fallingSeeds.erase(it);
      } else {
        ++it;
      }
    }

    if (kills >= WIN_KILLS) {
      running = false;
      won = true;
    }
  }

  void render(SDL_Renderer* r, TextRenderer& text) {
    drawRect(r, SDL_Rect{0, 0, WIN_W, WIN_H}, SDL_Color{47, 107, 47, 255});

    // Поле.
    for (int row = 0; row < ROWS; ++row) {
      for (int col = 0; col < COLS; ++col) {
        SDL_Color c = ((row + col) % 2 == 0) ? SDL_Color{139, 195, 74, 255}
                                              : SDL_Color{124, 179, 66, 255};
        drawRect(r,
                 SDL_Rect{FIELD_X + col * CELL, FIELD_Y + row * CELL, CELL,
                          CELL},
                 c);
      }
    }

    // HUD.
    drawRect(r, SDL_Rect{0, 0, WIN_W, FIELD_Y - 10},
              SDL_Color{139, 90, 43, 255});
    drawRect(r, seedCounterRect, SDL_Color{244, 228, 188, 255});
    text.draw("Семечки", seedCounterRect.x + 12, seedCounterRect.y + 8,
               SDL_Color{61, 43, 18, 255});
    text.draw(std::to_string(seeds), seedCounterRect.x + 12,
               seedCounterRect.y + 38, SDL_Color{61, 43, 18, 255});

    drawShopCard(r, text, oilCardRect, PlantType::OilShooter);
    drawShopCard(r, text, sunCardRect, PlantType::Sunflower);

    text.draw("Волна: " + std::to_string(wave), 555, 20,
               SDL_Color{244, 228, 188, 255});
    text.draw("Зомби: " + std::to_string(kills) + " / " +
                   std::to_string(WIN_KILLS),
               555, 46, SDL_Color{244, 228, 188, 255});

    // Растения.
    for (int row = 0; row < ROWS; ++row) {
      for (int col = 0; col < COLS; ++col) {
        auto& cell = grid[row][col];
        if (!cell.has_value()) continue;
        const PlantDef& def = plantDef(cell->type);
        int cx = FIELD_X + col * CELL + CELL / 2;
        int cy = FIELD_Y + row * CELL + CELL / 2;
        drawFilledCircle(r, cx, cy, 30, def.color);
        drawFilledCircle(r, cx, cy, 30, SDL_Color{40, 40, 40, 255});
        drawFilledCircle(r, cx, cy, 27, def.color);
        text.draw(def.shortLabel, cx, cy, SDL_Color{40, 30, 10, 255}, true);
        drawHpBar(r, FIELD_X + col * CELL + 20, FIELD_Y + row * CELL + 6, 50,
                   cell->hp, cell->maxHp);
      }
    }

    // Снаряды.
    for (auto& p : projectiles) {
      drawFilledCircle(r, FIELD_X + static_cast<int>(p.x),
                        FIELD_Y + static_cast<int>(p.y), 7,
                        SDL_Color{255, 214, 64, 255});
    }

    // Зомби.
    for (auto& z : zombies) {
      const ZombieDef& def = zombieDef(z.type);
      int cx = FIELD_X + static_cast<int>(z.x);
      int cy = FIELD_Y + z.row * CELL + CELL / 2;
      drawFilledCircle(r, cx, cy, 28, def.color);
      text.draw("З", cx, cy, SDL_Color{20, 20, 20, 255}, true);
      drawHpBar(r, cx - 25, FIELD_Y + z.row * CELL + 6, 50, z.hp, z.maxHp);
    }

    // Падающие/произведённые семечки.
    for (auto& s : fallingSeeds) {
      int cx = FIELD_X + static_cast<int>(s.x);
      int cy = FIELD_Y + static_cast<int>(s.y);
      drawFilledCircle(r, cx, cy, 12, SDL_Color{102, 187, 106, 255});
      drawFilledCircle(r, cx, cy, 9, SDL_Color{165, 214, 167, 255});
    }

    // Сетка при выборе растения.
    if (selectedPlant.has_value()) {
      SDL_SetRenderDrawColor(r, 255, 255, 255, 90);
      for (int col = 0; col <= COLS; ++col)
        SDL_RenderDrawLine(r, FIELD_X + col * CELL, FIELD_Y,
                            FIELD_X + col * CELL, FIELD_Y + FIELD_H);
      for (int row = 0; row <= ROWS; ++row)
        SDL_RenderDrawLine(r, FIELD_X, FIELD_Y + row * CELL,
                            FIELD_X + FIELD_W, FIELD_Y + row * CELL);
    }

    if (!running) {
      SDL_SetRenderDrawColor(r, 0, 0, 0, 170);
      SDL_Rect overlay{0, 0, WIN_W, WIN_H};
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
      SDL_RenderFillRect(r, &overlay);
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);

      std::string title =
          won ? "Победа!" : "Зомби съели ваш мозг!";
      std::string subtitle =
          won ? "Вы отбились от " + std::to_string(WIN_KILLS) +
                    " зомби с помощью Маслострела!"
              : "Зомби прорвались через лужайку.";
      text.draw(title, WIN_W / 2, WIN_H / 2 - 60,
                 SDL_Color{244, 228, 188, 255}, true);
      text.draw(subtitle, WIN_W / 2, WIN_H / 2 - 20,
                 SDL_Color{244, 228, 188, 255}, true);

      drawRect(r, restartBtnRect, SDL_Color{74, 122, 42, 255});
      text.draw("Начать заново", restartBtnRect.x + restartBtnRect.w / 2,
                 restartBtnRect.y + restartBtnRect.h / 2,
                 SDL_Color{255, 255, 255, 255}, true);
    }
  }

 private:
  static bool pointInRect(int x, int y, const SDL_Rect& rect) {
    return x >= rect.x && x < rect.x + rect.w && y >= rect.y &&
           y < rect.y + rect.h;
  }

  void toggleSelection(PlantType type) {
    const PlantDef& def = plantDef(type);
    if (seeds < def.cost) return;
    if (selectedPlant.has_value() && *selectedPlant == type)
      selectedPlant.reset();
    else
      selectedPlant = type;
  }

  void spawnZombie() {
    static std::mt19937 rng{std::random_device{}()};
    std::uniform_int_distribution<int> rowDist(0, ROWS - 1);
    const ZombieDef& def = zombieDef(ZombieType::Basic);
    zombies.push_back(Zombie{ZombieType::Basic, rowDist(rng),
                              static_cast<float>(FIELD_W + 20), def.hp,
                              def.hp, SDL_GetTicks()});
  }

  void spawnFallingSeedFromSky() {
    static std::mt19937 rng{std::random_device{}()};
    std::uniform_int_distribution<int> colDist(0, COLS - 1);
    int col = colDist(rng);
    float x = col * CELL + CELL / 2.0f;
    fallingSeeds.push_back(
        FallingSeed{x, -10.0f, CELL / 2.0f, 25, true, SDL_GetTicks()});
  }

  void drawShopCard(SDL_Renderer* r, TextRenderer& text, SDL_Rect rect,
                     PlantType type) {
    const PlantDef& def = plantDef(type);
    bool selected = selectedPlant.has_value() && *selectedPlant == type;
    bool affordable = seeds >= def.cost;

    SDL_Color bg = selected ? SDL_Color{255, 210, 63, 255}
                             : SDL_Color{207, 232, 176, 255};
    if (!affordable) bg = SDL_Color{160, 160, 160, 255};
    drawRect(r, rect, bg);
    drawRect(r, rect, SDL_Color{74, 122, 42, 255}, false);

    drawFilledCircle(r, rect.x + rect.w / 2, rect.y + 26, 18, def.color);
    text.draw(def.shortLabel, rect.x + rect.w / 2, rect.y + 26,
               SDL_Color{40, 30, 10, 255}, true);
    text.draw(def.name, rect.x + rect.w / 2, rect.y + 50,
               SDL_Color{20, 40, 60, 255}, true);
    text.draw(std::to_string(def.cost), rect.x + rect.w / 2, rect.y + 68,
               SDL_Color{122, 74, 0, 255}, true);
  }

  void drawHpBar(SDL_Renderer* r, int x, int y, int w, int hp, int maxHp) {
    float ratio = std::max(0.0f, static_cast<float>(hp) / maxHp);
    drawRect(r, SDL_Rect{x, y, w, 5}, SDL_Color{40, 40, 40, 255});
    drawRect(r, SDL_Rect{x, y, static_cast<int>(w * ratio), 5},
              SDL_Color{76, 175, 80, 255});
  }

  int seeds = 50;
  std::optional<PlantType> selectedPlant;
  std::array<std::array<std::optional<Plant>, COLS>, ROWS> grid;
  std::vector<Zombie> zombies;
  std::vector<Projectile> projectiles;
  std::vector<FallingSeed> fallingSeeds;
  int kills = 0;
  int wave = 1;
  Uint32 lastZombieSpawn = 0;
  Uint32 zombieSpawnIntervalMs = 5000;
  Uint32 lastSeedDrop = 0;
  Uint32 seedDropIntervalMs = 8000;
  bool running = true;
  bool won = false;
};

std::string findAssetPath(const std::string& relative) {
  const char* candidates[] = {"", "../", "../../"};
  for (const char* prefix : candidates) {
    std::string path = std::string(prefix) + relative;
    SDL_RWops* f = SDL_RWFromFile(path.c_str(), "rb");
    if (f) {
      SDL_RWclose(f);
      return path;
    }
  }
  return relative;
}

}  // namespace

int main(int, char**) {
  if (SDL_Init(SDL_INIT_VIDEO) != 0) {
    SDL_Log("SDL_Init error: %s", SDL_GetError());
    return 1;
  }
  if (TTF_Init() != 0) {
    SDL_Log("TTF_Init error: %s", TTF_GetError());
    SDL_Quit();
    return 1;
  }

  SDL_Window* window = SDL_CreateWindow(
      "PVZ-AI — Растения против зомби", SDL_WINDOWPOS_CENTERED,
      SDL_WINDOWPOS_CENTERED, WIN_W, WIN_H, SDL_WINDOW_SHOWN);
  if (!window) {
    SDL_Log("SDL_CreateWindow error: %s", SDL_GetError());
    return 1;
  }
  SDL_Renderer* renderer = SDL_CreateRenderer(
      window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
  if (!renderer) {
    renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_SOFTWARE);
  }

  std::string fontPath = findAssetPath("assets/DejaVuSans-Bold.ttf");
  TTF_Font* font = TTF_OpenFont(fontPath.c_str(), 16);
  TTF_Font* fontSmall = TTF_OpenFont(fontPath.c_str(), 13);
  if (!font || !fontSmall) {
    SDL_Log("TTF_OpenFont error (%s): %s", fontPath.c_str(), TTF_GetError());
    return 1;
  }

  TextRenderer text(renderer, fontSmall);
  Game game;

  bool quit = false;
  Uint32 lastTime = SDL_GetTicks();

  while (!quit) {
    SDL_Event e;
    while (SDL_PollEvent(&e)) {
      if (e.type == SDL_QUIT) quit = true;
      if (e.type == SDL_KEYDOWN && e.key.keysym.sym == SDLK_ESCAPE)
        quit = true;
      if (e.type == SDL_MOUSEBUTTONDOWN && e.button.button == SDL_BUTTON_LEFT)
        game.handleClick(e.button.x, e.button.y);
    }

    Uint32 now = SDL_GetTicks();
    Uint32 dt = now - lastTime;
    lastTime = now;
    game.update(now, dt);

    SDL_SetRenderDrawColor(renderer, 0, 0, 0, 255);
    SDL_RenderClear(renderer);
    game.render(renderer, text);
    SDL_RenderPresent(renderer);

    SDL_Delay(1000 / 60);
  }

  TTF_CloseFont(font);
  TTF_CloseFont(fontSmall);
  SDL_DestroyRenderer(renderer);
  SDL_DestroyWindow(window);
  TTF_Quit();
  SDL_Quit();
  return 0;
}
