package io.github.pikapikagfy.cowHeadZombiePlugin;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class CowHeadZombiePlugin extends JavaPlugin implements Listener {
    private Zombie boss;
    private boolean hasSummonedMinions = false;
    private Map<Zombie, BossBar> bossBars = new HashMap<>();
    private Map<Cow, Zombie> cowBossMap = new HashMap<>();
    private long lastRageChargeTime = 0; // 上一次愤怒冲撞的时间
    private long lastSummonTime = 0; // 上一次召唤僵尸疣猪兽的时间
    private static final long RAGE_CHARGE_COOLDOWN = 5000; // 愤怒冲撞的冷却时间
    private static final long SUMMON_COOLDOWN = 10000; // 召唤技能的冷却时间

    private static final long AREA_DAMAGE_COOLDOWN = 10000; // 10秒冷却时间
    private long lastAreaDamageTime = 0; // 上一次施放范围伤害的时间
    private static final long INVULNERABILITY_COOLDOWN = 15000; // 无敌状态的冷却时间为15秒

    private long lastInvulnerableTime = System.currentTimeMillis(); // 上一次进入无敌状态的时间




    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/spawnCowZombie")) {
            var player = event.getPlayer();
            var location = player.getLocation();

            // 创建牛和僵尸
            Cow cow = (Cow) location.getWorld().spawnEntity(location.add(new Vector(0, 1, 0)), EntityType.COW);
            boss = (Zombie) location.getWorld().spawnEntity(location.add(new Vector(0, 0, 0)), EntityType.ZOMBIE);

            // 禁用牛的AI
            cow.setAI(false);
            cow.setCollidable(false);
            cow.teleport(boss.getLocation().add(0, 1, 0));

            // Boss 行为逻辑
            setupBossBehavior(boss, cow);

            player.sendMessage("生成了一只牛和一只僵尸，它们已连接在一起！");
            event.setCancelled(true);
        }
    }
    // 给BOSS设置全套下届合金甲并添加装甲装饰
    private void equipNetheriteArmorWithTrim(Zombie boss) {
        // 创建下届合金盔甲的物品实例
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);

        // 创建装甲装饰 (ArmorTrim)，使用眼眸纹饰，镶嵌绿宝石
        ArmorTrim trim = new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.EYE);

        // 设置头盔装饰
        ItemMeta helmetMeta = helmet.getItemMeta();
        if (helmetMeta instanceof ArmorMeta) {
            ((ArmorMeta) helmetMeta).setTrim(trim); // 应用装饰
        }
        helmet.setItemMeta(helmetMeta);

        // 设置胸甲装饰
        ItemMeta chestplateMeta = chestplate.getItemMeta();
        if (chestplateMeta instanceof ArmorMeta) {
            ((ArmorMeta) chestplateMeta).setTrim(trim); // 应用装饰
        }
        chestplate.setItemMeta(chestplateMeta);

        // 设置护腿装饰
        ItemMeta leggingsMeta = leggings.getItemMeta();
        if (leggingsMeta instanceof ArmorMeta) {
            ((ArmorMeta) leggingsMeta).setTrim(trim); // 应用装饰
        }
        leggings.setItemMeta(leggingsMeta);

        // 设置靴子装饰
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta instanceof ArmorMeta) {
            ((ArmorMeta) bootsMeta).setTrim(trim); // 应用装饰
        }
        boots.setItemMeta(bootsMeta);

        // 给BOSS装备下届合金全套
        boss.getEquipment().setHelmet(helmet);
        boss.getEquipment().setChestplate(chestplate);
        boss.getEquipment().setLeggings(leggings);
        boss.getEquipment().setBoots(boots);
    }


    // 在 setupBossBehavior 方法中添加额外功能
    private void setupBossBehavior(Zombie boss, Cow cow) {
        // 设置BOSS属性
        boss.setCustomName("§c牛头僵尸");
        boss.setMaxHealth(200);
        boss.setHealth(200);
        boss.setCanPickupItems(false); // 防止捡起其他物品
        boss.setRemoveWhenFarAway(false);

        // 设置僵尸免疫火伤、摔落伤害、爆炸伤害
        boss.setFireTicks(0);
        boss.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(100); // 设置索敌范围为100格

        equipNetheriteArmorWithTrim(boss);

        // 每个tick执行的任务
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (boss == null || !boss.isValid()) {
                BossBar bossBar = bossBars.remove(boss);
                if (bossBar != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        bossBar.removePlayer(player);
                    }
                }
                return;
            }

            // 确保 Cow 始终跟随 BOSS
            cow.teleport(boss.getLocation().add(0, 1, 0));

            // 显示 Boss 血条
            showBossHealthBar(boss);

            // 检查 BOSS 状态并执行技能
            checkForRage(boss);
            performRandomSkill(boss);

        }, 0, 1); // 每个tick运行一次

        // 取消牛的伤害事件
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onCowDamage(EntityDamageEvent event) {
                // 确保牛存在并且是当前的特殊牛
                if (event.getEntity().equals(cow)) {
                    event.setCancelled(true); // 取消牛的所有伤害
                }
            }

            // 让僵尸免疫特定类型的伤害
            @EventHandler
            public void onBossDamage(EntityDamageEvent event) {
                if (event.getEntity().equals(boss)) {
                    if (event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                            event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
                            event.getCause() == EntityDamageEvent.DamageCause.LAVA ||
                            event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                            event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                            event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {

                        event.setCancelled(true); // 免疫火焰、摔落和爆炸伤害
                    }
                }
            }

            // 监听僵尸死亡事件，防止牛在僵尸死亡后被影响
            @EventHandler
            public void onBossDeath(EntityDeathEvent event) {
                if (event.getEntity().equals(boss)) {
                    // 确保僵尸死亡后，牛不会受到影响
                    if (cow != null && cow.isValid()) {
                        // 设置牛闪光
                        cow.setGlowing(true);
                        cow.setCustomName("§c牛头人的怒火");

                        // 播放凋零愤怒的声音
                        cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);

                        // 创建一个新的 BukkitRunnable 来实现变大和爆炸
                        new BukkitRunnable() {
                            private int ticksElapsed = 0;

                            @Override
                            public void run() {
                                ticksElapsed++;

                                // 让牛逐渐变大
                                if (ticksElapsed <= 60) { // 3秒 = 60个ticks
                                    // 按比例放大牛的体积
                                    for (int i = 0; i < 10; i++) {
                                        enlargeCow(cow);
                                        cow.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, cow.getLocation().add(0, 1, 0), 10, i * 0.5, 0.5, i * 0.5);
                                    }
                                } else {
                                    // 在牛变大结束后创建爆炸
                                    cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0F, 1.0F);
                                    cow.getWorld().createExplosion(cow.getLocation(), 8.0F, true, false);
                                    cow.remove(); // 移除牛
                                    cancel(); // 取消任务
                                }
                            }
                        }.runTaskTimer( CowHeadZombiePlugin.this, 0, 1);
                    }
                }
            }
        }, this);


    }
    public void enlargeCow(Cow cow) {
        // 创建一个新的属性修改器
        AttributeModifier scaleModifier = new AttributeModifier(
                NamespacedKey.randomKey(),
                1.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.HAND
        );
        cow.getAttribute(Attribute.GENERIC_SCALE).addModifier(scaleModifier); // 添加属性修改器

        new BukkitRunnable() {
            private int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;

                if (ticksElapsed <= 60) { // 3秒 = 60个ticks
                    // 生成粒子效果
                    for (int i = 0; i < 10; i++) {
                        cow.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, cow.getLocation().add(0, 1, 0), 10, i * 0.1, 0.5, i * 0.1);
                    }
                } else {
                    // 在牛变大结束后创建爆炸
                    cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0F, 1.0F);
                    cow.getWorld().createExplosion(cow.getLocation(), 4.0F, true, false);
                    cow.remove(); // 移除牛
                    cancel(); // 取消任务
                }
            }
        }.runTaskTimer(this, 0, 1); // 用你的插件实例替换
    }

    private void checkPlayerDistance(Zombie boss, Player player) {
        long currentTime = System.currentTimeMillis();

        // 每4秒检查一次距离

            // 获取玩家与Boss之间的距离
            double distance = boss.getLocation().distance(player.getLocation());

            // 如果距离超过5格，释放技能
            if (distance > 10) {
                performBullCharge(boss);
                broadcastSkillMessage(boss, "Boss 使用了技能：蛮牛飞跃");
            }
    }

    private long lastSkillTime = 0; // 记录上一次发动技能的时间
    private static final long SKILL_COOLDOWN = 5000; // 技能冷却时间设置为5秒

    private void performRandomSkill(Zombie boss) {
        long currentTime = System.currentTimeMillis();
        Random random = new Random();

        // 检查是否已经过了技能冷却时间
        if (currentTime - lastSkillTime >= SKILL_COOLDOWN && boss.getHealth() > boss.getMaxHealth() / 2) {
            // 定义技能数组
            String[] skills = {"bullRoar", "bullCharge", "wildSummon", "spinAttack", "performDashAttack"};
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getLocation().distance(boss.getLocation()) <15) { // 如果玩家在范围内
                    checkPlayerDistance(boss,player);
                }
            }


            // 随机选择技能并执行
            int skillIndex = random.nextInt(skills.length);
            switch (skills[skillIndex]) {
                case "bullRoar":
                    performBullRoar(boss);
                    broadcastSkillMessage(boss, "Boss 使用了技能：恐惧怒吼");
                    break;
                case "bullCharge":
                    performBullCharge(boss);
                    broadcastSkillMessage(boss, "Boss 使用了技能：蛮牛飞跃");
                    break;
                case "wildSummon":
                    performWildSummon(boss);
                    broadcastSkillMessage(boss, "Boss 使用了技能：野性召唤");
                    break;
                case "spinAttack":
                    performSpinAttack(boss);
                    broadcastSkillMessage(boss, "Boss 使用了技能：灵魂侵蚀");
                    break;
                case "performDashAttack":
                    performDashAttack(boss);
                    broadcastSkillMessage(boss, "Boss 使用了技能：冲刺攻击");
                    break;
            }

            // 更新上一次发动技能的时间
            lastSkillTime = currentTime;
        }
    }

    // 广播技能消息给所有玩家
    private void broadcastSkillMessage(Zombie boss, String message) {
        boss.getWorld().getPlayers().forEach(player -> player.sendMessage(message));
    }

    private void performBullRoar(Zombie boss) {
        // 前摇：播放主副手挥动动作并生成粒子效果
        boss.setAI(false);
        boss.swingMainHand();
        boss.swingOffHand();
        boss.getWorld().spawnParticle(Particle.SMOKE, boss.getLocation(), 10, 0.5, 0.5, 0.5, 1);

        // 延迟执行技能主要效果
        new BukkitRunnable() {
            @Override
            public void run() {
                // 播放牛头怒吼声音并施加减速和虚弱效果
                boss.getWorld().playSound(boss.getLocation(), "entity.ghast.scream", 1, 1);
                for (var player : Bukkit.getOnlinePlayers()) {
                    performDashAttack(boss);
                    if (player.getLocation().distance(boss.getLocation()) < 5) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 50, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 1));
                    }
                }
            }
        }.runTaskLater(this, 20); // 延迟1秒
        new BukkitRunnable() {
            @Override
            public void run() {
                boss.setAI(true);
                performDashAttack(boss);
            }
        }.runTaskLater(this, 30); // 延迟1秒
    }
    private void tryPerformInvulnerability(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 每40秒尝试进入无敌状态
        if (currentTime - lastInvulnerableTime >= INVULNERABILITY_COOLDOWN) {
            performInvulnerability(boss);
            lastInvulnerableTime = currentTime; // 更新时间
        }
    }

    private void performInvulnerability(Zombie boss) {
        boss.setInvulnerable(true); // 进入无敌状态
        boss.setAI(false);
        spawnBulletHell(boss); // 开始释放弹幕
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                boss.setInvulnerable(false); // 结束无敌状态
                boss.setAI(true);
                cancel(); // 停止任务
            }
        };
        runnable.runTaskLater(this, 200); // 5秒后结束无敌状态
    }

    private void spawnBulletHell(Zombie boss) {
        // 弹幕攻击逻辑
        for (int i = 0; i < 10; i++) {
            if(i%2==0) {
                explodeCow(boss);
            }
            Vector direction = new Vector(Math.random() - 0.5, 2, Math.random() - 0.5).normalize();
            boss.getWorld().spawn(boss.getLocation(), Arrow.class).setVelocity(direction.multiply(2));
        }
    }


    private void performBullCharge(Zombie boss) {
        boss.swingOffHand();
        boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        // 找到最近的玩家
        Player nearestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : Bukkit.getOnlinePlayers()) {
            double distance = player.getLocation().distance(boss.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            // 在BOSS的上方生成牛
            Location spawnLocation = boss.getLocation().add(0, 2, 0);
            Cow thrownCow = (Cow) boss.getWorld().spawnEntity(spawnLocation, EntityType.COW);
            thrownCow.setCollidable(false);
            thrownCow.setCustomName("§c冲击牛");

            // 计算投掷方向并施加更大的速度
            Vector direction = nearestPlayer.getLocation().add(0, 5, 0).toVector().subtract(spawnLocation.toVector()).normalize().multiply(1.5);
            thrownCow.setVelocity(direction);

            // 创建一个任务，监测牛的状态
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 检查牛是否在地面上
                    if (thrownCow.isOnGround()) {
                        // 瞬移BOSS到牛的位置
                        boss.teleport(thrownCow.getLocation());
                        thrownCow.remove(); // 移除牛
                        performDashAttack(boss);
                        cancel(); // 取消任务
                    }
                }
            }.runTaskTimer(this, 0, 1); // 每tick检查一次
        }
    }

    private void performWildSummon(Zombie boss) {
        // 前摇：主手挥动并生成粒子
        boss.swingMainHand();
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
        boss.setAI(false);
        int numberOfCows = 5; // 召唤的牛的数量
        List<Cow> cows = new ArrayList<>();

        for (int i = 0; i < numberOfCows; i++) {
                    // 在BOSS周围随机位置生成牛
            Location cowLocation = boss.getLocation().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            Cow shieldCow = (Cow) boss.getWorld().spawnEntity(cowLocation, EntityType.COW);
            shieldCow.setCustomName("§c爆炸牛");
            shieldCow.setAI(true);
            shieldCow.setCollidable(false);
            cows.add(shieldCow);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Cow cow : cows) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getLocation().distance(cow.getLocation()) < 10) { // 如果玩家在范围内
                            Location targetLocation = player.getLocation().add(0,3,0);
                            cow.setVelocity(targetLocation.toVector().subtract(cow.getLocation().toVector()).normalize().multiply(1.5)); // 向玩家冲去
                        }
                    }
                }
            }
        }.runTaskLater(this,40);

        // 创建一个延迟任务，让牛在3秒后爆炸
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Cow cow : cows) {
                    cow.getWorld().createExplosion(cow.getLocation(), 2.0F, true, false); // 创建爆炸
                    cow.remove(); // 移除牛
                    boss.setAI(true);
                    performBullCharge(boss);
                }
            }
        }.runTaskLater(this, 60); // 3秒后执行
    }



    private void performSpinAttack(Zombie boss) {
        // 前摇：副手挥动并生成粒子
        boss.swingOffHand();
        boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        // 召唤五只凋零骷髅
        List<WitherSkeleton> summonedSkeletons = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            WitherSkeleton skeleton = boss.getWorld().spawn(boss.getLocation(), WitherSkeleton.class);
            skeleton.setCustomName("§c凋零骷髅卫士");
            skeleton.setCustomNameVisible(true);
            summonedSkeletons.add(skeleton);
        }

        // 延迟执行旋转攻击
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 使用 BukkitRunnable 实现循环任务
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 100) { // 50 ticks = 5秒
                        // 技能结束后移除凋零骷髅
                        for (WitherSkeleton skeleton : summonedSkeletons) {
                            if (skeleton.isValid()) {
                                skeleton.remove();
                            }
                        }
                        this.cancel(); // 取消任务
                        return;
                    }

                    // 执行旋转攻击逻辑
                    for (var player : Bukkit.getOnlinePlayers()) {
                        // 判断玩家是否在攻击范围内
                        if (player.getLocation().distance(boss.getLocation()) < 5) {
                            player.damage(1);

                            // 在玩家位置生成粒子效果
                            boss.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                        }
                    }

                    // 旋转BOSS
                    boss.setRotation(boss.getLocation().getYaw() + 30, boss.getLocation().getPitch());

                    // 在BOSS周围生成粒子效果
                    boss.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, boss.getLocation(), 10, 1, 1, 1, 0.1);

                    ticks += 1;
                }
            }.runTaskTimer(this, 0, 1); // 每tick执行一次，持续5秒
        }, 20); // 延迟1秒执行
    }

    private void trySummonFloatingCow(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 每10秒尝试发动一次浮空牛技能，成功概率30%
        if (currentTime - lastSummonTime >= SUMMON_COOLDOWN) {
            lastSummonTime = currentTime; // 更新时间
            if (Math.random() < 0.3) { // 30%概率
                summonFloatingCow(boss); // 触发技能
            }
        }
    }



    private void checkForRage(Zombie boss) {
        // 检查BOSS的生命状态并处理愤怒状态
        if ( 10<=boss.getHealth() &&boss.getHealth() <= boss.getMaxHealth() / 2) {
            boss.setCustomName("§c愤怒的牛头僵尸");
            boss.setAbsorptionAmount(boss.getAbsorptionAmount() + 10); // 增加护盾效果
            tryPerformRageCharge(boss);
            tryExplodeCow(boss);
            trySummonFloatingCow(boss);
            tryPerformAreaDamage(boss);
            tryPerformInvulnerability(boss);
        }
    }
    private void displayAttackRange(Zombie boss,float r) {
        // 每次Boss施法时显示攻击范围
        for (int angle = 0; angle < 360; angle +=2) {
            double radian = Math.toRadians(angle);
            double x = r * Math.cos(radian);
            double z = r * Math.sin(radian);
            boss.getWorld().spawnParticle(Particle.GLOW, boss.getLocation().add(x, 0, z), 1); // 显示粒子
        }
    }
    private void tryPerformAreaDamage(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 每10秒施放一次范围大伤害
        if (currentTime - lastAreaDamageTime >= AREA_DAMAGE_COOLDOWN) {
            performAreaDamage(boss);
            lastAreaDamageTime = currentTime; // 更新时间
        }
    }

    private void performAreaDamage(Zombie boss) {
        // Boss进入施法状态
        boss.setAI(false);
        boss.setInvulnerable(true); // 进入无敌状态
        boss.setGlowing(true);
        boss.swingOffHand();
        boss.swingMainHand();

        // 施法持续3秒
        BukkitRunnable runnable = new BukkitRunnable() {
            int count = 3; // 施法时间
            @Override
            public void run() {
                if (count > 0) {
                    count--;
                } else {
                    boss.setInvulnerable(false); // 结束施法
                    // 对范围内玩家造成巨额伤害
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getLocation().distance(boss.getLocation()) < 5) {
                            player.damage(10); // 造成伤害
                            displayAttackRange(boss,5);
                            boss.setAI(true);

                        }
                    }
                    cancel(); // 停止任务
                }
            }
        };
        runnable.runTaskTimer(this , 0, 20); // 每秒执行一次
    }

    // 实际的浮空无敌牛技能
    private void summonFloatingCow(Zombie boss) {
        // 召唤一头牛
        Cow floatingCow = boss.getWorld().spawn(boss.getLocation().add(0, 1, 0), Cow.class);
        floatingCow.setCustomName("§b浮空牛");
        floatingCow.setCustomNameVisible(true); // 显示名称
        floatingCow.setGlowing(true);

        // 让僵尸进入无敌状态
        boss.setInvulnerable(true);

        // 创建定时任务让牛缓慢浮空，并且添加粒子特效
        new BukkitRunnable() {
            double height = 0;

            @Override
            public void run() {
                if (!floatingCow.isValid()) { // 如果牛被杀死了
                    // 对僵尸造成高额伤害
                    boss.damage(10.0); // 10点伤害，或根据需要调整

                    // 取消无敌状态
                    boss.setInvulnerable(false);

                    this.cancel(); // 停止任务
                    return;
                }

                // 牛浮空逻辑
                if (height >= 10) { // 如果牛达到10格高度
                    floatingCow.remove(); // 移除牛
                    boss.setInvulnerable(false); // 取消无敌状态
                    this.cancel(); // 停止任务
                    return;
                }

                // 缓慢上升0.1格
                floatingCow.teleport(floatingCow.getLocation().add(0, 0.1, 0));
                height += 0.1;

                // 创建粒子效果
                Location cowLocation = floatingCow.getLocation();
                Location bossLocation = boss.getLocation().add(0, 1, 0); // 僵尸头顶位置

                // 生成牛和僵尸之间的粒子效果
                createParticleEffect(cowLocation, bossLocation);
            }
        }.runTaskTimer(this, 0L, 2L); // 每2 tick运行一次，浮空速度每次0.1格，约每秒0.5格
    }

    // 粒子特效生成方法
    private void createParticleEffect(Location from, Location to) {
        // 计算牛和僵尸之间的方向向量
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);
        double stepSize = 0.2; // 每步生成的粒子距离，可以调整

        // 沿着牛和僵尸之间的路径生成粒子
        for (double i = 0; i < distance; i += stepSize) {
            Location particleLocation = from.clone().add(direction.multiply(i));
            from.getWorld().spawnParticle(Particle.GLOW, particleLocation, 10);
        }
    }



    private void tryPerformRageCharge(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 每5秒尝试发动一次愤怒冲撞，成功概率50%
        if (currentTime - lastRageChargeTime >= RAGE_CHARGE_COOLDOWN && Math.random() < 0.5) {
            performRageCharge(boss);
            lastRageChargeTime = currentTime; // 更新时间
        }
    }


    private void performRageCharge(Zombie boss) {
        // 增加药水效果
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1)); // 给予速度效果，持续5秒
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1)); // 给予力量效果，持续5秒

        // 提升BOSS速度
        Vector newVelocity = boss.getLocation().getDirection().normalize().multiply(2); // 增加速度
        boss.setVelocity(newVelocity); // 移动 BOSS

        // 碰撞玩家并触发爆炸效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().distance(boss.getLocation()) < 2) {
                player.damage(1); // 增加伤害
                Vector knockback = player.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(-2);
                player.setVelocity(knockback); // 施加击退
                boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation(), 1); // 触发爆炸效果
            }
        }

        boss.getWorld().playSound(boss.getLocation(), "entity.zombie.attack_iron_door", 1, 1);
    }

    private long lastExplodeTime = 0;
    private static final long EXPLODE_COOLDOWN = 5000; // 冷却时间为5秒
    private void tryExplodeCow(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 每10秒尝试发动一次喷射爆炸牛技能，成功概率20%
        if (currentTime - lastExplodeTime >= EXPLODE_COOLDOWN) {
            lastExplodeTime = currentTime; // 更新时间
            if (Math.random() < 0.8) { // 20%概率
                explodeCow(boss); // 触发技能
            }
        }
    }

    // 实际的喷射爆炸牛技能
    private void explodeCow(Zombie boss) {
        // 召唤一头牛
        Cow explodingCow = boss.getWorld().spawn(boss.getLocation().add(0, 1, 0), Cow.class);
        explodingCow.setCustomName("§c爆炸牛");
        explodingCow.setCustomNameVisible(true); // 显示名称

        // 寻找最近的玩家
        Player targetPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : boss.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(boss.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                targetPlayer = player;
            }
        }

        // 如果有玩家，向玩家移动
        if (targetPlayer != null) {
            final Player finalTargetPlayer = targetPlayer;
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 检查牛是否还存在
                    if (!explodingCow.isValid() || !finalTargetPlayer.isOnline()) {
                        this.cancel();
                        return;
                    }

                    // 计算牛朝向玩家的方向向量
                    Location cowLocation = explodingCow.getLocation();
                    Location targetLocation = finalTargetPlayer.getLocation();

                    Vector direction = targetLocation.toVector().subtract(cowLocation.toVector()).normalize();
                    explodingCow.setVelocity(direction.multiply(0.5)); // 设置牛的移动速度，0.5可以调节

                    // 判断牛与玩家的距离，如果距离非常近，则爆炸
                    if (cowLocation.distance(targetLocation) < 2) {
                        explodingCow.getWorld().createExplosion(explodingCow.getLocation(), 1.0F, false, false); // 创建爆炸
                        explodingCow.remove(); // 移除牛
                        this.cancel();
                    }
                }
            }.runTaskTimer(this, 0L, 1L); // 每个tick运行一次，确保牛不断向玩家移动
        }

        // 2秒后自动爆炸
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (explodingCow.isValid()) { // 确保牛还存在
                // 在牛的位置制造爆炸，但不会破坏方块
                boss.getWorld().createExplosion(explodingCow.getLocation(), 1.0F, false, false);
                explodingCow.remove(); // 移除牛
            }
        }, 40L); // 2秒 = 40 tick
    }


    private void showBossHealthBar(Zombie boss) {
        // 检查是否已有 BossBar
        BossBar bossBar = bossBars.get(boss);
        if (bossBar == null) {
            // 创建新的 BossBar
            bossBar = Bukkit.createBossBar("§c牛头僵尸的生命", BarColor.RED, BarStyle.SOLID);
            bossBars.put(boss, bossBar);
        }

        // 更新 BossBar 的生命值
        double healthPercentage = boss.getHealth() / boss.getMaxHealth();
        bossBar.setProgress(healthPercentage);

        // 显示给所有在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    // 当 BOSS 死亡时，移除 BossBar
    private void removeBossHealthBar(Zombie boss) {
        BossBar bossBar = bossBars.remove(boss);
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removePlayer(player);
            }
        }
    }
    private void summonMinions(Zombie boss) {
        // 召唤小僵尸并产生粒子效果
        hasSummonedMinions = true;
        boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation(), 30);

        // 召唤5个小僵尸
        for (int i = 0; i < 5; i++) {
            Zombie minion = (Zombie) boss.getWorld().spawnEntity(boss.getLocation().add(i, 0, 0), EntityType.ZOMBIE);
            minion.setTarget(boss.getTarget()); // 将小僵尸的目标设为玩家
            minion.setCustomName("§7小僵尸");
            minion.setHealth(10); // 小僵尸的血量
        }
    }

    private long lastDashAttackTime = 0; // 记录上次冲刺攻击的时间
    private static final long DASH_ATTACK_CD = 5000; // 冲刺攻击冷却时间，单位为毫秒
    private Random random = new Random(); // 随机数生成器

    private void performDashAttack(Zombie boss) {
        long currentTime = System.currentTimeMillis();

        // 检查冷却时间
        if (currentTime - lastDashAttackTime < DASH_ATTACK_CD) {
            return; // 如果在冷却时间内，直接返回
        }

        // 执行冲刺攻击
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 30);
        boss.getWorld().playSound(boss.getLocation(), "entity.enderman.teleport", 1, 1);

        // 瞄准玩家并进行瞬移
        var targetPlayer = boss.getTarget();
        if (targetPlayer != null) {
            Location targetLocation = targetPlayer.getLocation();
            double randomYOffset = random.nextDouble() * 2 - 1; // 随机Y轴偏移量
            boss.teleport(targetLocation.add(0, 1 + randomYOffset, 0)); // 瞬移到玩家位置上方
            targetLocation.getWorld().createExplosion(targetLocation, 1);
        }

        lastDashAttackTime = currentTime; // 更新上次攻击时间
    }
}