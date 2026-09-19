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
constexpr Uint32 PREP_MS = 15000;  // время на подготовку перед первым зомби

constexpr float OIL_CUBE_CHANCE = 0.12f;
constexpr float OIL_CUBE_DAMAGE_MULT = 1.5f;
constexpr Uint32 OIL_CUBE_SLOW_MS = 3000;
constexpr float OIL_CUBE_SLOW_MULT = 0.5f;

constexpr int WAVE_COUNT = 5;
constexpr int WAVE_ZOMBIE_COUNTS[WAVE_COUNT] = {3, 4, 5, 6, 7};
constexpr Uint32 WAVE_BREAK_MS = 10000;  // перерыв между волнами

Uint32 waveBaseInterval(int waveNum) {
  return std::max<Uint32>(3000, 8000 - static_cast<Uint32>(waveNum - 1) * 1000);
}

enum class PlantType { OilShooter, Sunflower, Daisy };
enum class ZombieType { Basic, Conehead, Crazy, Mutant };

struct PlantDef {
  std::string name;
  int cost;
  int hp;
  Uint32 actionRateMs;  // скорострельность/скорость производства/перезарядка
                         // лазера (для Ромашки)
  int damage;           // урон снаряда/лазера
  int produceAmount;    // количество семечек за раз (для подсолнуха)
  Uint32 plantCooldownMs;  // время до повторного доступа к посадке в магазине
};

struct ZombieDef {
  std::string name;
  int hp;
  float speed;  // px / ms
  int biteDamage;
  Uint32 attackRateMs;
};

const PlantDef& plantDef(PlantType t) {
  static const PlantDef oil{"Маслострел", 100, 100, 1950, 20, 0, 8000};
  static const PlantDef sun{"Подсолнух", 50, 80, 12000, 0, 25, 5000};
  // Ромашка: раз в actionRateMs лепестки резко раскручиваются и она
  // стреляет лазером по ближайшему зомби в ряду — 30 урона и отталкивает
  // его на одну клетку назад. Уникальная фишка, которой нет в
  // оригинальной PVZ.
  static const PlantDef daisy{"Ромашка", 175, 90, 15000, 30, 0, 10000};
  switch (t) {
    case PlantType::Sunflower:
      return sun;
    case PlantType::Daisy:
      return daisy;
    default:
      return oil;
  }
}

const ZombieDef& zombieDef(ZombieType t) {
  static const ZombieDef basic{"Зомби", 150, 0.01288f, 34, 700};
  static const ZombieDef conehead{"Конусоголовый", 420, 0.01288f, 34, 700};
  static const ZombieDef crazy{"Безумный", 113, 0.03864f, 34, 700};
  static const ZombieDef mutant{"Мутант", 840, 0.01288f, 34, 700};
  switch (t) {
    case ZombieType::Conehead:
      return conehead;
    case ZombieType::Crazy:
      return crazy;
    case ZombieType::Mutant:
      return mutant;
    default:
      return basic;
  }
}

struct Plant {
  PlantType type;
  int hp;
  int maxHp;
  int row;
  int col;
  Uint32 lastActionMs;
  bool lastShotWasOilCube = false;
};

struct Zombie {
  ZombieType type;
  int row;
  float x;  // относительно левого края поля
  int hp;
  int maxHp;
  Uint32 lastAttackMs;
  float animPhase;  // случайный сдвиг фазы шага, чтобы зомби не шагали в такт
  bool blocked;      // true, если сейчас грызёт растение (не идёт)
  Uint32 slowUntil = 0;  // до этого момента движется в 2 раза медленнее
};

struct Projectile {
  float x, y;
  int row;
  int damage;
  float speed;
  bool isOilCube;  // редкий особый выстрел: больше урона и замедляет зомби
};

struct FallingSeed {
  float x, y, targetY;
  int amount;
  bool falling;
  Uint32 bornAt;
};

SDL_Rect seedCounterRect{15, 15, 150, 80};
SDL_Rect oilCardRect{175, 15, 90, 80};
SDL_Rect sunCardRect{270, 15, 90, 80};
SDL_Rect daisyCardRect{365, 15, 90, 80};
SDL_Rect shovelRect{460, 15, 80, 80};
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

// Яркая, хорошо заметная на газоне семечка: тёмная обводка + сочная
// жёлто-зелёная заливка + светлый блик.
void drawSeedIcon(SDL_Renderer* r, int cx, int cy, int radius) {
  drawFilledCircle(r, cx, cy, radius, SDL_Color{50, 40, 10, 255});
  drawFilledCircle(r, cx, cy, radius - 2, SDL_Color{223, 194, 58, 255});
  drawFilledCircle(r, cx - radius / 4, cy - radius / 4, radius / 3,
                    SDL_Color{255, 245, 190, 255});
}

SDL_Color lerpColor(SDL_Color a, SDL_Color b, float t) {
  t = std::clamp(t, 0.0f, 1.0f);
  return SDL_Color{
      static_cast<Uint8>(a.r + (b.r - a.r) * t),
      static_cast<Uint8>(a.g + (b.g - a.g) * t),
      static_cast<Uint8>(a.b + (b.b - a.b) * t), 255};
}

void drawRect(SDL_Renderer* r, SDL_Rect rect, SDL_Color c, bool filled = true) {
  SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
  if (filled)
    SDL_RenderFillRect(r, &rect);
  else
    SDL_RenderDrawRect(r, &rect);
}

// Кубик масла — редкий особый снаряд Маслострела и значок на лице зомби,
// в которого он попал. Рисуется как кубик с более светлой верхней гранью
// и бликом, чтобы явно отличаться от круглой обычной капли.
void drawOilCubeIcon(SDL_Renderer* r, int cx, int cy, int half) {
  SDL_Color outline{120, 90, 20, 255};
  SDL_Color topFace{255, 250, 225, 255};
  SDL_Color frontFace{235, 196, 90, 255};
  SDL_Color shine{255, 255, 255, 255};
  drawRect(r, SDL_Rect{cx - half - 1, cy - half - 1, half * 2 + 2, half * 2 + 2},
           outline);
  drawRect(r, SDL_Rect{cx - half, cy - half, half * 2, half}, topFace);
  drawRect(r, SDL_Rect{cx - half, cy, half * 2, half}, frontFace);
  int shineSize = std::max(1, half / 3);
  drawRect(r, SDL_Rect{cx - half + 2, cy + 2, shineSize, shineSize}, shine);
}

// Заливка выпуклого многоугольника (веером треугольников).
void drawFilledPolygon(SDL_Renderer* r, const std::vector<SDL_FPoint>& pts,
                        SDL_Color color) {
  if (pts.size() < 3) return;
  std::vector<SDL_Vertex> verts(pts.size());
  for (size_t i = 0; i < pts.size(); ++i) {
    verts[i].position = pts[i];
    verts[i].color = color;
    verts[i].tex_coord = SDL_FPoint{0, 0};
  }
  std::vector<int> indices;
  indices.reserve((pts.size() - 2) * 3);
  for (size_t i = 1; i + 1 < pts.size(); ++i) {
    indices.push_back(0);
    indices.push_back(static_cast<int>(i));
    indices.push_back(static_cast<int>(i + 1));
  }
  SDL_RenderGeometry(r, nullptr, verts.data(), static_cast<int>(verts.size()),
                      indices.data(), static_cast<int>(indices.size()));
}

// Общий глиняный горшок для обоих растений.
void drawPlantPot(SDL_Renderer* r, int cx, int potTopY, int potBottomY,
                   float scale) {
  auto off = [scale](float v) { return v * scale; };
  SDL_Color potOutline{74, 51, 36, 255};
  SDL_Color potColor{121, 85, 61, 255};
  drawFilledPolygon(
      r,
      {{float(cx) - off(24), float(potTopY) - off(2)},
       {float(cx) + off(24), float(potTopY) - off(2)},
       {float(cx) + off(15), float(potBottomY) + off(2)},
       {float(cx) - off(15), float(potBottomY) + off(2)}},
      potOutline);
  drawFilledPolygon(r,
                     {{float(cx) - off(20), float(potTopY)},
                      {float(cx) + off(20), float(potTopY)},
                      {float(cx) + off(13), float(potBottomY)},
                      {float(cx) - off(13), float(potBottomY)}},
                     potColor);
}

// Пара листьев у основания стебля — с тёмной обводкой для контраста с
// травой.
void drawPlantLeaves(SDL_Renderer* r, int cx, int leafBaseY, float scale) {
  auto off = [scale](float v) { return v * scale; };
  SDL_Color leafOutline{20, 60, 26, 255};
  SDL_Color leafColor{62, 150, 60, 255};
  auto drawLeaf = [&](float dir) {
    std::vector<SDL_FPoint> outline{
        {float(cx) + dir * off(2), float(leafBaseY) - off(3)},
        {float(cx) + dir * off(42), float(leafBaseY) - off(20)},
        {float(cx) + dir * off(10), float(leafBaseY) + off(10)}};
    std::vector<SDL_FPoint> fill{
        {float(cx) + dir * off(3), float(leafBaseY) - off(2)},
        {float(cx) + dir * off(36), float(leafBaseY) - off(16)},
        {float(cx) + dir * off(9), float(leafBaseY) + off(7)}};
    drawFilledPolygon(r, outline, leafOutline);
    drawFilledPolygon(r, fill, leafColor);
  };
  drawLeaf(-1.0f);
  drawLeaf(1.0f);
}

// Лопата для выкапывания растений: черенок с ручкой и металлическое лезвие.
void drawShovelIcon(SDL_Renderer* r, int cx, int cy) {
  SDL_Color handleOutline{70, 45, 20, 255};
  SDL_Color handleColor{150, 105, 55, 255};
  drawFilledCircle(r, cx, cy - 24, 6, handleOutline);
  drawFilledCircle(r, cx, cy - 24, 4, SDL_Color{180, 130, 70, 255});
  drawRect(r, SDL_Rect{cx - 4, cy - 22, 8, 26}, handleOutline);
  drawRect(r, SDL_Rect{cx - 3, cy - 21, 6, 24}, handleColor);

  SDL_Color bladeOutline{70, 72, 78, 255};
  SDL_Color bladeColor{182, 190, 198, 255};
  drawFilledPolygon(r,
                     {{float(cx) - 13, float(cy) + 1},
                      {float(cx) + 13, float(cy) + 1},
                      {float(cx) + 9, float(cy) + 22},
                      {float(cx) - 9, float(cy) + 22}},
                     bladeOutline);
  drawFilledPolygon(r,
                     {{float(cx) - 10, float(cy) + 3},
                      {float(cx) + 10, float(cy) + 3},
                      {float(cx) + 7, float(cy) + 19},
                      {float(cx) - 7, float(cy) + 19}},
                     bladeColor);
}

// Рисует Маслострел как настоящее растение (горшок, стебель, листья,
// голова с лицом и "маслянный" ствол-пушка), а не как кружок.
// cx/cyCenter — центр клетки; scale уменьшает иконку для карточки магазина;
// bobOffset — лёгкое покачивание головы (px); recoilAmount (0..1) — отдача
// при выстреле, тянет голову назад и зажигает вспышку у дула. oilCubeShot
// делает эту отдачу уникальной: голова раздувается сильнее, вспышка — в
// виде кубика масла, а не круглая.
void drawOilShooter(SDL_Renderer* r, int cx, int cyCenter, float scale,
                     float bobOffset = 0.0f, float recoilAmount = 0.0f,
                     bool oilCubeShot = false) {
  auto off = [scale](float v) { return v * scale; };

  int headY = cyCenter - static_cast<int>(off(20)) -
              static_cast<int>(bobOffset * scale);
  int headR = std::max(4, static_cast<int>(off(26)));
  int potTopY = cyCenter + static_cast<int>(off(16));
  int potBottomY = cyCenter + static_cast<int>(off(34));

  drawPlantPot(r, cx, potTopY, potBottomY, scale);

  // Ствол — тёмный, чтобы не сливаться с газоном.
  SDL_Color stemColor{34, 90, 40, 255};
  int stemW = std::max(2, static_cast<int>(off(10)));
  int stemTop = headY + headR - static_cast<int>(off(6));
  drawRect(r, SDL_Rect{cx - stemW / 2, stemTop, stemW, potTopY - stemTop},
           stemColor);

  drawPlantLeaves(r, cx, potTopY - static_cast<int>(off(4)), scale);

  float kick = oilCubeShot ? recoilAmount * 1.8f : recoilAmount;
  int hcx = cx - static_cast<int>(kick * off(6));
  int puff = oilCubeShot ? static_cast<int>(off(5) * recoilAmount) : 0;
  int headRAnim = headR + puff;

  drawFilledCircle(r, hcx, headY, headRAnim, SDL_Color{40, 40, 40, 255});
  drawFilledCircle(r, hcx, headY, std::max(3, headRAnim - 3),
                    SDL_Color{235, 193, 60, 255});

  SDL_Color spoutColor{70, 60, 25, 255};
  int spoutW = std::max(4, static_cast<int>(off(20)));
  int spoutH = std::max(3, static_cast<int>(off(14)));
  int spoutX = hcx + static_cast<int>(off(16)) + puff;
  drawRect(r, SDL_Rect{spoutX, headY - spoutH / 2, spoutW, spoutH},
           spoutColor);
  int muzzleX = spoutX + spoutW;
  int muzzleR = std::max(2, static_cast<int>(off(7)));
  drawFilledCircle(r, muzzleX, headY, muzzleR,
                    oilCubeShot ? SDL_Color{255, 250, 225, 255}
                                : SDL_Color{255, 224, 120, 255});

  if (recoilAmount > 0.01f) {
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
    if (oilCubeShot) {
      int flashHalf =
          std::max(5, static_cast<int>(off(10) + off(14) * recoilAmount));
      drawOilCubeIcon(r, muzzleX + static_cast<int>(off(8)), headY, flashHalf);
    } else {
      Uint8 flashAlpha = static_cast<Uint8>(220 * recoilAmount);
      int flashR =
          std::max(3, static_cast<int>(off(6) + off(10) * recoilAmount));
      drawFilledCircle(r, muzzleX + static_cast<int>(off(6)), headY, flashR,
                        SDL_Color{255, 245, 200, flashAlpha});
    }
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
  }

  int eyeR = std::max(1, static_cast<int>(off(3)));
  drawFilledCircle(r, hcx - static_cast<int>(off(8)), headY - static_cast<int>(off(4)),
                    eyeR, SDL_Color{30, 30, 30, 255});
  drawFilledCircle(r, hcx + static_cast<int>(off(2)), headY - static_cast<int>(off(4)),
                    eyeR, SDL_Color{30, 30, 30, 255});
}

// Рисует Подсолнух как настоящее растение (горшок, стебель, листья и
// цветок с лепестками вокруг тёмной сердцевины), а не как кружок.
// bobOffset — лёгкое покачивание в режиме ожидания; popAmount (0..1) —
// импульс в момент производства семечки: лепестки распускаются шире и
// вокруг цветка загорается тёплое свечение.
void drawSunflower(SDL_Renderer* r, int cx, int cyCenter, float scale,
                    float bobOffset = 0.0f, float popAmount = 0.0f) {
  auto off = [scale](float v) { return v * scale; };

  int headY = cyCenter - static_cast<int>(off(24)) -
              static_cast<int>(bobOffset * scale);
  int potTopY = cyCenter + static_cast<int>(off(16));
  int potBottomY = cyCenter + static_cast<int>(off(34));

  drawPlantPot(r, cx, potTopY, potBottomY, scale);

  SDL_Color stemColor{34, 90, 40, 255};
  int stemW = std::max(2, static_cast<int>(off(9)));
  int stemTop = headY + static_cast<int>(off(18));
  drawRect(r, SDL_Rect{cx - stemW / 2, stemTop, stemW, potTopY - stemTop},
           stemColor);

  drawPlantLeaves(r, cx, potTopY - static_cast<int>(off(4)), scale);

  if (popAmount > 0.01f) {
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
    Uint8 glowAlpha = static_cast<Uint8>(150 * popAmount);
    int glowR = static_cast<int>(off(32) + off(14) * popAmount);
    drawFilledCircle(r, cx, headY, glowR,
                      SDL_Color{255, 241, 168, glowAlpha});
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
  }

  // Лепестки по кругу вокруг сердцевины — на импульсе распускаются шире
  // и светлеют.
  const int PETAL_COUNT = 10;
  float petalGrow = 1.0f + popAmount * 0.3f;
  float petalOrbit = off(23) * petalGrow;
  int petalR = std::max(3, static_cast<int>(off(11) * petalGrow));
  SDL_Color petalOutline{150, 90, 10, 255};
  SDL_Color petalColor =
      lerpColor(SDL_Color{255, 196, 20, 255}, SDL_Color{255, 240, 160, 255},
                popAmount);
  for (int i = 0; i < PETAL_COUNT; ++i) {
    float angle = (6.28318530f * i) / PETAL_COUNT;
    int px = cx + static_cast<int>(std::cos(angle) * petalOrbit);
    int py = headY + static_cast<int>(std::sin(angle) * petalOrbit);
    drawFilledCircle(r, px, py, petalR + 2, petalOutline);
    drawFilledCircle(r, px, py, petalR, petalColor);
  }

  // Сердцевина с лицом.
  int centerR = std::max(4, static_cast<int>(off(19)));
  drawFilledCircle(r, cx, headY, centerR + 3, SDL_Color{40, 40, 40, 255});
  drawFilledCircle(r, cx, headY, centerR, SDL_Color{121, 74, 38, 255});

  int eyeR = std::max(1, static_cast<int>(off(2)));
  drawFilledCircle(r, cx - static_cast<int>(off(6)), headY - static_cast<int>(off(2)),
                    eyeR, SDL_Color{25, 15, 8, 255});
  drawFilledCircle(r, cx + static_cast<int>(off(6)), headY - static_cast<int>(off(2)),
                    eyeR, SDL_Color{25, 15, 8, 255});
}

// Рисует Ромашку — растение с белыми лепестками вокруг жёлтой (при
// выстреле — раскалённой) сердцевины. Её уникальная фишка (которой нет в
// оригинальной PVZ): раз в actionRateMs лепестки резко раскручиваются и
// она бьёт лазером по ближайшему зомби в ряду, отталкивая его на одну
// клетку назад. petalAngle крутит лепестки (быстро сразу после выстрела,
// с затуханием); glow (0..1) — накал сердцевины в тот же момент.
// bobOffset — лёгкое покачивание в покое.
void drawDaisy(SDL_Renderer* r, int cx, int cyCenter, float scale,
                float bobOffset = 0.0f, float petalAngle = 0.0f,
                float glow = 0.0f) {
  auto off = [scale](float v) { return v * scale; };

  int headY = cyCenter - static_cast<int>(off(24)) -
              static_cast<int>(bobOffset * scale);
  int potTopY = cyCenter + static_cast<int>(off(16));
  int potBottomY = cyCenter + static_cast<int>(off(34));

  drawPlantPot(r, cx, potTopY, potBottomY, scale);

  SDL_Color stemColor{34, 90, 40, 255};
  int stemW = std::max(2, static_cast<int>(off(9)));
  int stemTop = headY + static_cast<int>(off(18));
  drawRect(r, SDL_Rect{cx - stemW / 2, stemTop, stemW, potTopY - stemTop},
           stemColor);

  drawPlantLeaves(r, cx, potTopY - static_cast<int>(off(4)), scale);

  // Лепестки — белые, вращаются вокруг сердцевины.
  const int PETAL_COUNT = 8;
  float petalOrbit = off(24);
  int petalR = std::max(3, static_cast<int>(off(10)));
  SDL_Color petalOutline{195, 195, 175, 255};
  SDL_Color petalColor{255, 255, 250, 255};
  for (int i = 0; i < PETAL_COUNT; ++i) {
    float angle = petalAngle + (6.28318530f * i) / PETAL_COUNT;
    int px = cx + static_cast<int>(std::cos(angle) * petalOrbit);
    int py = headY + static_cast<int>(std::sin(angle) * petalOrbit);
    drawFilledCircle(r, px, py, petalR + 2, petalOutline);
    drawFilledCircle(r, px, py, petalR, petalColor);
  }

  // Сердцевина — накаляется докрасна в момент выстрела.
  if (glow > 0.05f) {
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
    Uint8 glowAlpha = static_cast<Uint8>(160 * glow);
    int glowR = static_cast<int>(off(20) + off(12) * glow);
    drawFilledCircle(r, cx, headY, glowR, SDL_Color{255, 110, 60, glowAlpha});
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
  }
  int centerR = std::max(4, static_cast<int>(off(17)));
  SDL_Color centerColor =
      lerpColor(SDL_Color{235, 196, 40, 255}, SDL_Color{255, 90, 40, 255}, glow);
  drawFilledCircle(r, cx, headY, centerR + 3, SDL_Color{40, 40, 40, 255});
  drawFilledCircle(r, cx, headY, centerR, centerColor);

  int eyeR = std::max(1, static_cast<int>(off(2)));
  drawFilledCircle(r, cx - static_cast<int>(off(6)), headY - static_cast<int>(off(2)),
                    eyeR, SDL_Color{25, 15, 8, 255});
  drawFilledCircle(r, cx + static_cast<int>(off(6)), headY - static_cast<int>(off(2)),
                    eyeR, SDL_Color{25, 15, 8, 255});
}

// Рисует зомби как фигуру (ноги, туловище, руки, голова), а не кружок.
// walkPhase крутит ноги/руки по циклу ходьбы; blocked останавливает ходьбу
// (зомби грызёт растение); type определяет облик: Conehead — конус на
// голове, Crazy — красные глаза и более резкий, размашистый бег, Mutant —
// крупнее и с шрамом-швом на лбу; slowed рисует кубик масла на лице, пока
// зомби замедлен попаданием.
void drawZombie(SDL_Renderer* r, int cx, int cyCenter, float walkPhase,
                 bool blocked, ZombieType type, bool slowed) {
  bool isConehead = type == ZombieType::Conehead;
  bool isCrazy = type == ZombieType::Crazy;
  bool isMutant = type == ZombieType::Mutant;

  float scale = isMutant ? 1.3f : 1.0f;
  auto sc = [scale](float v) { return static_cast<int>(v * scale); };

  int headR = sc(15);
  int headY = cyCenter - sc(24);
  int torsoW = sc(24);
  int torsoTop = headY + headR - sc(2);
  int torsoH = sc(26);
  int torsoBottom = torsoTop + torsoH;

  float strideAmp = isCrazy ? 8.0f : 5.0f;
  float armAmp = isCrazy ? 7.0f : 4.0f;
  float stride = blocked ? 0.0f : std::sin(walkPhase) * strideAmp * scale;
  float armSwing =
      blocked ? 6.0f : std::sin(walkPhase + 3.14159f) * armAmp * scale;

  SDL_Color skinColor =
      isMutant ? SDL_Color{150, 120, 150, 255} : SDL_Color{140, 158, 118, 255};
  SDL_Color skinOutline =
      isMutant ? SDL_Color{60, 40, 60, 255} : SDL_Color{45, 55, 35, 255};
  SDL_Color shirtColor{92, 100, 68, 255};
  SDL_Color shirtOutline{40, 45, 28, 255};
  SDL_Color pantsColor{64, 68, 56, 255};

  // Ноги — шагают попеременно (у безумного — размашистее).
  int legW = sc(9), legH = sc(20);
  int legY = torsoBottom - sc(4);
  drawRect(r, SDL_Rect{cx - sc(10) + static_cast<int>(stride), legY, legW, legH},
           pantsColor);
  drawRect(r, SDL_Rect{cx + sc(1) - static_cast<int>(stride), legY, legW, legH},
           pantsColor);

  // Руки — качаются в противофазе к ногам; при атаке вытянуты вперёд.
  int armW = sc(7), armH = sc(22);
  int armY = torsoTop + sc(2);
  drawRect(r,
           SDL_Rect{cx - torsoW / 2 - armW + 2,
                    armY + static_cast<int>(blocked ? -4 : armSwing), armW,
                    armH},
           skinColor);
  drawRect(r,
           SDL_Rect{cx + torsoW / 2 - 2,
                    armY + static_cast<int>(blocked ? -4 : -armSwing), armW,
                    armH},
           skinColor);

  // Рваная рубаха.
  drawRect(r, SDL_Rect{cx - torsoW / 2 - 1, torsoTop - 1, torsoW + 2,
                        torsoH + 2},
           shirtOutline);
  drawRect(r, SDL_Rect{cx - torsoW / 2, torsoTop, torsoW, torsoH},
           shirtColor);
  drawFilledPolygon(
      r,
      {{float(cx - torsoW / 2), float(torsoBottom - 6)},
       {float(cx - torsoW / 2 + 8), float(torsoBottom - 6)},
       {float(cx - torsoW / 2 + 4), float(torsoBottom + 4)}},
      shirtOutline);

  // Голова с запавшими глазами (у безумного — красные).
  drawFilledCircle(r, cx, headY, headR + 2, skinOutline);
  drawFilledCircle(r, cx, headY, headR, skinColor);
  SDL_Color eyeColor =
      isCrazy ? SDL_Color{220, 20, 20, 255} : SDL_Color{20, 15, 10, 255};
  int eyeR = isCrazy ? 3 : 2;
  drawFilledCircle(r, cx - sc(5), headY - sc(2), eyeR, eyeColor);
  drawFilledCircle(r, cx + sc(5), headY - sc(2), eyeR, eyeColor);
  drawRect(r, SDL_Rect{cx - 5, headY + sc(6), 10, 2}, SDL_Color{50, 25, 20, 255});

  if (isMutant) {
    // Шрам-шов на лбу.
    SDL_Color scarColor{35, 25, 35, 255};
    int scarY = headY - sc(9);
    drawRect(r, SDL_Rect{cx - 11, scarY, 22, 2}, scarColor);
    for (int i = -9; i <= 9; i += 6)
      drawRect(r, SDL_Rect{cx + i, scarY - 3, 2, 8}, scarColor);
  }

  if (isConehead) {
    SDL_Color coneOutline{130, 65, 8, 255};
    SDL_Color coneColor{255, 140, 26, 255};
    int baseY = headY - headR + 5;
    int topY = headY - headR - 14;
    drawFilledPolygon(r,
                       {{float(cx), float(topY) - 2},
                        {float(cx) + 15, float(baseY) + 2},
                        {float(cx) - 15, float(baseY) + 2}},
                       coneOutline);
    drawFilledPolygon(r,
                       {{float(cx), float(topY)},
                        {float(cx) + 12, float(baseY)},
                        {float(cx) - 12, float(baseY)}},
                       coneColor);
    int stripeY = baseY - 7;
    drawFilledPolygon(r,
                       {{float(cx) - 9, float(stripeY)},
                        {float(cx) + 9, float(stripeY)},
                        {float(cx) + 7, float(stripeY) + 4},
                        {float(cx) - 7, float(stripeY) + 4}},
                       SDL_Color{255, 255, 255, 255});
  }

  if (slowed) {
    // Кубик масла "прилип" прямо на лицо, а не рядом с полоской здоровья.
    drawOilCubeIcon(r, cx + 4, headY + 3, 7);
  }
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
    shovelSelected = false;
    plantReadyAt.fill(0);
    for (auto& row : grid)
      for (auto& cell : row) cell.reset();
    zombies.clear();
    projectiles.clear();
    fallingSeeds.clear();
    kills = 0;
    currentWave = 1;
    killsThisWave = 0;
    spawnedThisWave = 0;
    betweenWaves = false;
    waveBreakEndTime = 0;
    zombieSpawnIntervalMs = waveBaseInterval(currentWave);
    buildWavePlan(currentWave);
    seedDropIntervalMs = 8000;
    running = true;
    won = false;
    Uint32 now = SDL_GetTicks();
    gameStartTime = now;
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
      if (std::sqrt(dx * dx + dy * dy) < 28) {
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
    if (pointInRect(mx, my, daisyCardRect)) {
      toggleSelection(PlantType::Daisy);
      return;
    }
    if (pointInRect(mx, my, shovelRect)) {
      selectedPlant.reset();
      shovelSelected = !shovelSelected;
      return;
    }

    int col = (mx - FIELD_X) / CELL;
    int row = (my - FIELD_Y) / CELL;
    bool onField = col >= 0 && col < COLS && row >= 0 && row < ROWS &&
                   mx >= FIELD_X && my >= FIELD_Y;
    if (!onField) return;

    if (shovelSelected) {
      if (grid[row][col].has_value()) {
        grid[row][col].reset();
        shovelSelected = false;
      }
      return;
    }

    if (!selectedPlant.has_value()) return;
    if (grid[row][col].has_value()) return;

    const PlantDef& def = plantDef(*selectedPlant);
    if (seeds < def.cost) return;

    seeds -= def.cost;
    Uint32 placedAt = SDL_GetTicks();
    grid[row][col] = Plant{*selectedPlant, def.hp, def.hp, row, col, placedAt};
    plantReadyAt[static_cast<int>(*selectedPlant)] = placedAt + def.plantCooldownMs;
    selectedPlant.reset();
  }

  void update(Uint32 now, Uint32 dt) {
    if (!running) return;

    bool inPrep = (now - gameStartTime) < PREP_MS;
    int waveTarget = WAVE_ZOMBIE_COUNTS[currentWave - 1];
    bool canSpawn = !inPrep && !betweenWaves && spawnedThisWave < waveTarget;
    if (canSpawn && now - lastZombieSpawn > zombieSpawnIntervalMs) {
      spawnZombie();
      lastZombieSpawn = now;
      ++spawnedThisWave;
      zombieSpawnIntervalMs =
          std::max<Uint32>(3000, zombieSpawnIntervalMs - 400);
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
            // Совпадает с положением дула в drawOilShooter (headY смещён
            // на off(20) вверх, дуло выступает на off(36) вправо от головы).
            float py = row * CELL + CELL / 2.0f - 20.0f;
            float px = col * CELL + CELL / 2.0f + 36.0f;
            static std::mt19937 fireRng{std::random_device{}()};
            static std::uniform_real_distribution<float> chance01(0.0f, 1.0f);
            bool oilCube = chance01(fireRng) < OIL_CUBE_CHANCE;
            int dmg = oilCube
                          ? static_cast<int>(def.damage * OIL_CUBE_DAMAGE_MULT)
                          : def.damage;
            projectiles.push_back(Projectile{px, py, row, dmg, 0.4f, oilCube});
            plant.lastActionMs = now;
            plant.lastShotWasOilCube = oilCube;
          }
        } else if (plant.type == PlantType::Sunflower) {
          if (now - plant.lastActionMs > def.actionRateMs) {
            float cx = col * CELL + CELL / 2.0f;
            float cy = row * CELL + CELL / 2.0f - 20.0f;
            fallingSeeds.push_back(
                FallingSeed{cx, cy, cy, def.produceAmount, false, now});
            plant.lastActionMs = now;
          }
        } else if (plant.type == PlantType::Daisy) {
          // Уникальная фишка: раз в actionRateMs лепестки раскручиваются
          // и она бьёт лазером по ближайшему зомби в ряду — урон и
          // отталкивание на одну клетку назад.
          bool hasTarget = std::any_of(
              zombies.begin(), zombies.end(), [&](const Zombie& z) {
                return z.row == row && z.x > static_cast<float>(col * CELL);
              });
          if (hasTarget && now - plant.lastActionMs > def.actionRateMs) {
            Zombie* nearest = nullptr;
            for (auto& z : zombies) {
              if (z.row == row && z.x > static_cast<float>(col * CELL)) {
                if (!nearest || z.x < nearest->x) nearest = &z;
              }
            }
            if (nearest) {
              nearest->hp -= def.damage;
              nearest->x = std::min(nearest->x + static_cast<float>(CELL),
                                     static_cast<float>(FIELD_W + 20));
              plant.lastActionMs = now;
            }
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
          if (it->isOilCube) z.slowUntil = now + OIL_CUBE_SLOW_MS;
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

    // Мутанты при смерти выпускают трёх безумных, каждого на свою линию;
    // сами новые зомби добавляются после цикла, чтобы не инвалидировать
    // итератор.
    std::vector<float> mutantDeathXs;

    for (auto it = zombies.begin(); it != zombies.end();) {
      const ZombieDef& def = zombieDef(it->type);
      if (it->hp <= 0) {
        if (it->type == ZombieType::Mutant) {
          mutantDeathXs.push_back(it->x);
        }
        it = zombies.erase(it);
        ++kills;
        ++killsThisWave;
        continue;
      }

      int col = static_cast<int>(it->x) / CELL;
      Plant* plant = (col >= 0 && col < COLS && grid[it->row][col].has_value())
                         ? &(*grid[it->row][col])
                         : nullptr;
      bool blocked = plant && (it->x - col * CELL) < CELL * 0.6f &&
                     it->x > static_cast<float>(col * CELL);
      it->blocked = blocked;

      if (blocked) {
        if (now - it->lastAttackMs > def.attackRateMs) {
          plant->hp -= def.biteDamage;
          it->lastAttackMs = now;
          if (plant->hp <= 0) grid[it->row][col].reset();
        }
      } else {
        float speedMult = now < it->slowUntil ? OIL_CUBE_SLOW_MULT : 1.0f;
        it->x -= def.speed * speedMult * static_cast<float>(dt);
      }

      if (it->x <= -10.0f) {
        running = false;
        won = false;
        return;
      }
      ++it;
    }

    if (!mutantDeathXs.empty()) {
      static std::mt19937 rng{std::random_device{}()};
      std::uniform_real_distribution<float> phaseDist(0.0f, 6.28318530f);
      const ZombieDef& crazyDef = zombieDef(ZombieType::Crazy);
      for (float deathX : mutantDeathXs) {
        std::array<int, ROWS> rows;
        for (int i = 0; i < ROWS; ++i) rows[i] = i;
        std::shuffle(rows.begin(), rows.end(), rng);
        int spawnCount = std::min(3, ROWS);
        for (int i = 0; i < spawnCount; ++i) {
          zombies.push_back(Zombie{ZombieType::Crazy, rows[i], deathX,
                                    crazyDef.hp, crazyDef.hp, SDL_GetTicks(),
                                    phaseDist(rng), false});
        }
      }
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

    if (!betweenWaves && spawnedThisWave >= waveTarget && zombies.empty()) {
      if (currentWave >= WAVE_COUNT) {
        running = false;
        won = true;
      } else {
        betweenWaves = true;
        waveBreakEndTime = now + WAVE_BREAK_MS;
      }
    }

    if (betweenWaves && now >= waveBreakEndTime) {
      betweenWaves = false;
      ++currentWave;
      killsThisWave = 0;
      spawnedThisWave = 0;
      zombieSpawnIntervalMs = waveBaseInterval(currentWave);
      buildWavePlan(currentWave);
      lastZombieSpawn = now - zombieSpawnIntervalMs - 1;
    }
  }

  void render(SDL_Renderer* r, TextRenderer& text, TextRenderer& textMed,
              TextRenderer& textBig, Uint32 now) {
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
    drawRect(r, seedCounterRect, SDL_Color{74, 122, 42, 255}, false);
    drawSeedIcon(r, seedCounterRect.x + 26, seedCounterRect.y + 50, 14);
    text.draw("Семечки", seedCounterRect.x + 48, seedCounterRect.y + 8,
               SDL_Color{61, 43, 18, 255});
    textMed.draw(std::to_string(seeds), seedCounterRect.x + 48,
                  seedCounterRect.y + 34, SDL_Color{110, 74, 10, 255});

    drawShopCard(r, text, oilCardRect, PlantType::OilShooter, now);
    drawShopCard(r, text, sunCardRect, PlantType::Sunflower, now);
    drawShopCard(r, text, daisyCardRect, PlantType::Daisy, now);

    {
      SDL_Color bg = shovelSelected ? SDL_Color{255, 210, 63, 255}
                                     : SDL_Color{207, 232, 176, 255};
      drawRect(r, shovelRect, bg);
      drawRect(r, shovelRect, SDL_Color{74, 122, 42, 255}, false);
      drawShovelIcon(r, shovelRect.x + shovelRect.w / 2, shovelRect.y + 34);
      text.draw("Лопата", shovelRect.x + shovelRect.w / 2, shovelRect.y + 60,
                 SDL_Color{20, 40, 60, 255}, true);
    }

    text.draw("Волна: " + std::to_string(currentWave) + " / " +
                   std::to_string(WAVE_COUNT),
               555, 20, SDL_Color{244, 228, 188, 255});
    text.draw("Зомби: " + std::to_string(killsThisWave) + " / " +
                   std::to_string(WAVE_ZOMBIE_COUNTS[currentWave - 1]),
               555, 46, SDL_Color{244, 228, 188, 255});

    // Растения.
    for (int row = 0; row < ROWS; ++row) {
      for (int col = 0; col < COLS; ++col) {
        auto& cell = grid[row][col];
        if (!cell.has_value()) continue;
        int cx = FIELD_X + col * CELL + CELL / 2;
        int cy = FIELD_Y + row * CELL + CELL / 2;
        if (cell->type == PlantType::OilShooter) {
          float bob = std::sin(now / 260.0) * 2.0f;
          Uint32 sinceAction = now - cell->lastActionMs;
          float recoil = sinceAction < 140
                             ? (1.0f - sinceAction / 140.0f)
                             : 0.0f;
          drawOilShooter(r, cx, cy, 1.0f, bob, recoil,
                         cell->lastShotWasOilCube);
        } else if (cell->type == PlantType::Sunflower) {
          float bob = std::sin(now / 300.0) * 2.0f;
          Uint32 sinceAction = now - cell->lastActionMs;
          float pop = sinceAction < 400 ? (1.0f - sinceAction / 400.0f) : 0.0f;
          drawSunflower(r, cx, cy, 1.0f, bob, pop);
        } else {
          float bob = std::sin(now / 320.0) * 2.0f;
          Uint32 sinceAction = now - cell->lastActionMs;
          float spinT = std::min(1.0f, sinceAction / 500.0f);
          float eased = 1.0f - (1.0f - spinT) * (1.0f - spinT);
          float petalAngle = eased * 3.0f * 6.28318530f;
          float glow = sinceAction < 500 ? (1.0f - spinT) : 0.0f;
          drawDaisy(r, cx, cy, 1.0f, bob, petalAngle, glow);

          if (sinceAction < 250) {
            float beamAlpha = 1.0f - (sinceAction / 250.0f);
            SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
            Uint8 a = static_cast<Uint8>(220 * beamAlpha);
            SDL_Rect beam{cx + 10, cy - 3, (FIELD_X + FIELD_W) - (cx + 10), 6};
            if (beam.w > 0) drawRect(r, beam, SDL_Color{255, 90, 60, a});
            SDL_Rect beamCore{cx + 10, cy - 1, beam.w, 2};
            if (beamCore.w > 0)
              drawRect(r, beamCore, SDL_Color{255, 220, 200, a});
            SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
          }
        }
        drawHpBar(r, FIELD_X + col * CELL + 20, FIELD_Y + row * CELL + 6, 50,
                   cell->hp, cell->maxHp);
      }
    }

    // Снаряды — обычная капля кружком, редкий кубик масла отдельным значком.
    for (auto& p : projectiles) {
      int px = FIELD_X + static_cast<int>(p.x);
      int py = FIELD_Y + static_cast<int>(p.y);
      if (p.isOilCube) {
        drawOilCubeIcon(r, px, py, 13);
      } else {
        drawFilledCircle(r, px, py, 7, SDL_Color{255, 214, 64, 255});
      }
    }

    // Зомби (у безумного — заметно более частая, дёрганая походка).
    for (auto& z : zombies) {
      int cx = FIELD_X + static_cast<int>(z.x);
      int cy = FIELD_Y + z.row * CELL + CELL / 2;
      double phaseSpeed = z.type == ZombieType::Crazy ? 55.0 : 130.0;
      float walkPhase = z.animPhase + now / phaseSpeed;
      drawZombie(r, cx, cy, walkPhase, z.blocked, z.type, now < z.slowUntil);
      drawHpBar(r, cx - 25, FIELD_Y + z.row * CELL + 6, 50, z.hp, z.maxHp);
    }

    // Падающие/произведённые семечки — крупные и яркие, чтобы не терялись
    // на фоне газона.
    for (auto& s : fallingSeeds) {
      int cx = FIELD_X + static_cast<int>(s.x);
      int cy = FIELD_Y + static_cast<int>(s.y);
      drawSeedIcon(r, cx, cy, 17);
      text.draw("+" + std::to_string(s.amount), cx, cy - 26,
                 SDL_Color{255, 250, 210, 255}, true);
    }

    // Баннер подготовки перед первой волной зомби / паузы между волнами.
    Uint32 elapsed = now >= gameStartTime ? now - gameStartTime : 0;
    std::string bannerMsg;
    if (elapsed < PREP_MS) {
      Uint32 remainingSec = (PREP_MS - elapsed + 999) / 1000;
      bannerMsg =
          "Приготовьтесь! Зомби через " + std::to_string(remainingSec) + " с";
    } else if (betweenWaves) {
      Uint32 remain = waveBreakEndTime > now ? waveBreakEndTime - now : 0;
      Uint32 remainingSec = (remain + 999) / 1000;
      bannerMsg = "Волна " + std::to_string(currentWave) +
                  " пройдена! Следующая через " + std::to_string(remainingSec) +
                  " с";
    }
    if (!bannerMsg.empty()) {
      SDL_Rect banner{FIELD_X, FIELD_Y + FIELD_H / 2 - 32, FIELD_W, 64};
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
      SDL_SetRenderDrawColor(r, 0, 0, 0, 150);
      SDL_RenderFillRect(r, &banner);
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
      textBig.draw(bannerMsg, WIN_W / 2, FIELD_Y + FIELD_H / 2,
                   SDL_Color{255, 235, 180, 255}, true);
    }

    // Сетка при выборе растения или лопаты (лопата — оранжевая подсветка).
    if (selectedPlant.has_value() || shovelSelected) {
      if (shovelSelected)
        SDL_SetRenderDrawColor(r, 255, 140, 60, 110);
      else
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

      int totalZombies = 0;
      for (int c : WAVE_ZOMBIE_COUNTS) totalZombies += c;
      std::string title =
          won ? "Победа!" : "Зомби съели ваш мозг!";
      std::string subtitle =
          won ? "Вы отбились от всех " + std::to_string(WAVE_COUNT) +
                    " волн (" + std::to_string(totalZombies) + " зомби)!"
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
    if (SDL_GetTicks() < plantReadyAt[static_cast<int>(type)]) return;
    shovelSelected = false;
    if (selectedPlant.has_value() && *selectedPlant == type)
      selectedPlant.reset();
    else
      selectedPlant = type;
  }

  // Составляет план волны: сколько зомби из неё будут конусоголовыми
  // (с 3-й волны — 1/2/3 штуки), в случайном порядке появления.
  // Составляет план волны: сколько зомби из неё будут конусоголовыми
  // (с 3-й волны — 1/2/3 штуки), безумными (по одному с 2-й волны) и
  // мутантами (по одному с 4-й волны), в случайном порядке появления.
  void buildWavePlan(int waveNum) {
    int total = WAVE_ZOMBIE_COUNTS[waveNum - 1];
    int coneheads = waveNum >= 3 ? std::min(waveNum - 2, total) : 0;
    int crazies = waveNum >= 2 ? 1 : 0;
    int mutants = waveNum >= 4 ? 1 : 0;
    int special = std::min(coneheads + crazies + mutants, total);
    int basics = total - special;

    waveSpawnPlan.clear();
    waveSpawnPlan.insert(waveSpawnPlan.end(), basics, ZombieType::Basic);
    waveSpawnPlan.insert(waveSpawnPlan.end(), coneheads, ZombieType::Conehead);
    waveSpawnPlan.insert(waveSpawnPlan.end(), crazies, ZombieType::Crazy);
    waveSpawnPlan.insert(waveSpawnPlan.end(), mutants, ZombieType::Mutant);
    static std::mt19937 rng{std::random_device{}()};
    std::shuffle(waveSpawnPlan.begin(), waveSpawnPlan.end(), rng);
  }

  void spawnZombie() {
    static std::mt19937 rng{std::random_device{}()};
    std::uniform_int_distribution<int> rowDist(0, ROWS - 1);
    std::uniform_real_distribution<float> phaseDist(0.0f, 6.28318530f);
    ZombieType type = spawnedThisWave < static_cast<int>(waveSpawnPlan.size())
                          ? waveSpawnPlan[spawnedThisWave]
                          : ZombieType::Basic;
    const ZombieDef& def = zombieDef(type);
    zombies.push_back(Zombie{type, rowDist(rng),
                              static_cast<float>(FIELD_W + 20), def.hp,
                              def.hp, SDL_GetTicks(), phaseDist(rng), false});
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
                     PlantType type, Uint32 now) {
    const PlantDef& def = plantDef(type);
    bool selected = selectedPlant.has_value() && *selectedPlant == type;
    bool affordable = seeds >= def.cost;
    Uint32 readyAt = plantReadyAt[static_cast<int>(type)];
    bool onCooldown = now < readyAt;

    SDL_Color bg = selected ? SDL_Color{255, 210, 63, 255}
                             : SDL_Color{207, 232, 176, 255};
    if (!affordable || onCooldown) bg = SDL_Color{160, 160, 160, 255};
    drawRect(r, rect, bg);
    drawRect(r, rect, SDL_Color{74, 122, 42, 255}, false);

    if (type == PlantType::OilShooter) {
      drawOilShooter(r, rect.x + rect.w / 2, rect.y + 28, 0.45f);
    } else if (type == PlantType::Sunflower) {
      drawSunflower(r, rect.x + rect.w / 2, rect.y + 30, 0.45f);
    } else {
      drawDaisy(r, rect.x + rect.w / 2, rect.y + 30, 0.45f);
    }
    text.draw(def.name, rect.x + rect.w / 2, rect.y + 50,
               SDL_Color{20, 40, 60, 255}, true);
    text.draw(std::to_string(def.cost), rect.x + rect.w / 2, rect.y + 68,
               SDL_Color{122, 74, 0, 255}, true);

    if (onCooldown) {
      Uint32 remain = readyAt - now;
      float frac = std::min(
          1.0f, static_cast<float>(remain) / def.plantCooldownMs);
      int overlayH = static_cast<int>(rect.h * frac);
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
      drawRect(r, SDL_Rect{rect.x, rect.y, rect.w, overlayH},
               SDL_Color{20, 25, 15, 170});
      SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
      Uint32 sec = (remain + 999) / 1000;
      text.draw(std::to_string(sec), rect.x + rect.w / 2, rect.y + rect.h / 2 - 8,
                 SDL_Color{255, 255, 255, 255}, true);
    }
  }

  void drawHpBar(SDL_Renderer* r, int x, int y, int w, int hp, int maxHp) {
    float ratio = std::max(0.0f, static_cast<float>(hp) / maxHp);
    drawRect(r, SDL_Rect{x, y, w, 5}, SDL_Color{40, 40, 40, 255});
    drawRect(r, SDL_Rect{x, y, static_cast<int>(w * ratio), 5},
              SDL_Color{76, 175, 80, 255});
  }

  int seeds = 50;
  std::optional<PlantType> selectedPlant;
  std::array<Uint32, 3> plantReadyAt{0, 0, 0};
  bool shovelSelected = false;
  std::array<std::array<std::optional<Plant>, COLS>, ROWS> grid;
  std::vector<Zombie> zombies;
  std::vector<Projectile> projectiles;
  std::vector<FallingSeed> fallingSeeds;
  int kills = 0;
  int currentWave = 1;
  int killsThisWave = 0;
  int spawnedThisWave = 0;
  bool betweenWaves = false;
  Uint32 waveBreakEndTime = 0;
  std::vector<ZombieType> waveSpawnPlan;
  Uint32 gameStartTime = 0;
  Uint32 lastZombieSpawn = 0;
  Uint32 zombieSpawnIntervalMs = 8000;
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
  TTF_Font* fontSmall = TTF_OpenFont(fontPath.c_str(), 13);
  TTF_Font* fontMed = TTF_OpenFont(fontPath.c_str(), 20);
  TTF_Font* fontBig = TTF_OpenFont(fontPath.c_str(), 26);
  if (!fontSmall || !fontMed || !fontBig) {
    SDL_Log("TTF_OpenFont error (%s): %s", fontPath.c_str(), TTF_GetError());
    return 1;
  }

  TextRenderer text(renderer, fontSmall);
  TextRenderer textMed(renderer, fontMed);
  TextRenderer textBig(renderer, fontBig);
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
    game.render(renderer, text, textMed, textBig, now);
    SDL_RenderPresent(renderer);

    SDL_Delay(1000 / 60);
  }

  TTF_CloseFont(fontSmall);
  TTF_CloseFont(fontMed);
  TTF_CloseFont(fontBig);
  SDL_DestroyRenderer(renderer);
  SDL_DestroyWindow(window);
  TTF_Quit();
  SDL_Quit();
  return 0;
}
