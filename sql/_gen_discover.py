# -*- coding: utf-8 -*-
"""生成 100 条「发现/广场」模拟数据 SQL：
   - 12 个模拟用户 (mock-user-xxx)
   - 100 条行程发布 (trip_publish, 含路线/封面/标签/点赞收藏数)
   - 配套结构化路线：trip_plans + trip_days + trip_spots (详情页时间线/地图用)
   - 约 460 条评论 (comment, 含少量楼中楼回复)
   - 50 条关注关系 (follow_relation)
   使用 INSERT IGNORE，可重复执行，id 带 mock 前缀不与现有 seed 冲突。
"""
import json
import random
from datetime import datetime, timedelta

random.seed(20260709)

# ---------------------------------------------------------------------------
# 城市数据：province / 封面图 picsum id / 景点池 / 适用玩法 / 中心经纬度
# ---------------------------------------------------------------------------
CITY_DATA = {
    "北京":   ("北京", 1020, ["故宫","天安门广场","八达岭长城","颐和园","天坛","南锣鼓巷","798艺术区","国家博物馆","雍和宫","什刹海","恭王府"], ["亲子","文化","摄影","打卡"], (39.9042,116.4074)),
    "成都":   ("四川", 1015, ["宽窄巷子","锦里","武侯祠","大熊猫基地","春熙路","人民公园","都江堰","青城山","玉林路小酒馆","东郊记忆"], ["美食","打卡","亲子","摄影"], (30.5728,104.0668)),
    "厦门":   ("福建", 1018, ["鼓浪屿","曾厝垵","南普陀寺","环岛路","沙坡尾","中山路","植物园","集美学村","胡里山炮台"], ["文艺","海岛","打卡","美食"], (24.4798,118.0894)),
    "大理":   ("云南", 1019, ["洱海","苍山","大理古城","喜洲古镇","双廊","沙溪古镇","崇圣寺三塔","理想邦"], ["自然风光","徒步","文艺","摄影"], (25.6065,100.2676)),
    "西安":   ("陕西", 1022, ["兵马俑","华清池","古城墙","回民街","大雁塔","碑林博物馆","陕西历史博物馆","大唐不夜城"], ["文化","美食","打卡","摄影"], (34.3416,108.9398)),
    "三亚":   ("海南", 1024, ["亚龙湾","蜈支洲岛","南山寺","天涯海角","第一市场","海棠湾","分界洲岛","椰梦长廊"], ["海岛","打卡","美食","自然风光"], (18.2528,109.5119)),
    "重庆":   ("重庆", 1029, ["洪崖洞","磁器口","解放碑","长江索道","李子坝轻轨","南山一棵树","鹅岭二厂","朝天门"], ["美食","打卡","文艺","文化"], (29.4316,106.9123)),
    "丽江":   ("云南", 1036, ["丽江古城","玉龙雪山","束河古镇","蓝月谷","拉市海","泸沽湖","黑龙潭","玉湖村"], ["自然风光","文艺","徒步","摄影"], (26.8721,100.2299)),
    "杭州":   ("浙江", 1039, ["西湖","灵隐寺","河坊街","宋城","太子湾公园","西溪湿地","良渚古城","千岛湖"], ["亲子","自然风光","文艺","打卡"], (30.2741,120.1551)),
    "青岛":   ("山东", 1040, ["栈桥","八大关","崂山","啤酒街","劈柴院","金沙滩","小鱼山","奥帆中心"], ["美食","海岛","打卡","文艺"], (36.0671,120.3826)),
    "桂林":   ("广西", 1041, ["漓江","阳朔西街","十里画廊","龙脊梯田","象鼻山","银子岩","遇龙河","兴坪古镇"], ["自然风光","摄影","徒步","文艺"], (25.2736,110.2900)),
    "上海":   ("上海", 1042, ["外滩","南京路","迪士尼乐园","田子坊","豫园","新天地","陆家嘴","武康路"], ["打卡","亲子","文艺","文化"], (31.2304,121.4737)),
    "南京":   ("江苏", 1043, ["中山陵","夫子庙","先锋书店","总统府","玄武湖","老门东","鸡鸣寺","颐和路"], ["文化","美食","文艺","摄影"], (32.0603,118.7969)),
    "长沙":   ("湖南", 1045, ["太平街","坡子街","超级文和友","橘子洲","岳麓山","湖南省博物馆","IFS","谢子龙影像馆"], ["美食","打卡","文化","文艺"], (28.2282,112.9388)),
    "拉萨":   ("西藏", 1047, ["布达拉宫","大昭寺","八廓街","纳木错","羊卓雍错","哲蚌寺","罗布林卡","扎基寺"], ["文化","自然风光","摄影","徒步"], (29.6520,91.1721)),
    "苏州":   ("江苏", 1049, ["拙政园","平江路","虎丘","山塘街","苏州博物馆","金鸡湖","同里古镇","留园"], ["文化","文艺","亲子","打卡"], (31.2989,120.5853)),
    "敦煌":   ("甘肃", 1050, ["莫高窟","鸣沙山月牙泉","雅丹魔鬼城","敦煌夜市","玉门关","阳关","雷音寺","党河风情线"], ["文化","摄影","小众","自然风光"], (40.1421,94.6618)),
    "武汉":   ("湖北", 1051, ["黄鹤楼","户部巷","粮道街","东湖绿道","江汉路","昙华林","晴川阁","武汉大学"], ["美食","文化","打卡","文艺"], (30.5928,114.3055)),
    "哈尔滨": ("黑龙江", 1052, ["冰雪大世界","中央大街","圣索菲亚大教堂","松花江","老道外","太阳岛","伏尔加庄园","犹太老会堂"], ["打卡","文化","摄影","亲子"], (45.8038,126.5347)),
    "九寨沟": ("四川", 1053, ["九寨沟景区","黄龙","五花海","诺日朗瀑布","镜海","树正群海","长海","五彩池"], ["自然风光","摄影","徒步","小众"], (33.2600,103.9180)),
    "青海湖": ("青海", 1055, ["青海湖","茶卡盐湖","祁连山草原","门源油菜花","塔尔寺","黑马河","鸟岛","金银滩"], ["自然风光","自驾","摄影","徒步"], (36.8920,100.1800)),
    "成都川西": ("四川", 1057, ["稻城亚丁","四姑娘山","新都桥","塔公草原","海螺沟","丹巴藏寨","色达","党岭"], ["自然风光","徒步","摄影","小众"], (30.0500,101.9700)),
    "广州":   ("广东", 1060, ["广州塔","沙面","北京路","陈家祠","上下九","永庆坊","白云山","长隆"], ["美食","打卡","亲子","文化"], (23.1291,113.2644)),
    "婺源":   ("江西", 1061, ["篁岭","江湾","李坑","晓起","月亮湾","江岭油菜花","卧龙谷","思溪延村"], ["自然风光","摄影","文艺","小众"], (29.2700,117.8700)),
}

# ---------------------------------------------------------------------------
# 玩法（theme）
# ---------------------------------------------------------------------------
THEMES = {
    "亲子":   ("带娃嗨玩路线", "带娃嗨玩路线", ["亲子","轻松"], "带小朋友行程节奏放慢，每个点之间留足休息时间，记得备好零食和水。"),
    "美食":   ("舌尖上的寻味之旅", "舌尖寻味之旅", ["美食","必吃"], "当地菜市场和小巷子里的老店往往比网红店更地道，跟着本地人排队准没错。"),
    "文化":   ("穿越千年的文化巡礼", "文化巡礼", ["文化","历史"], "博物馆建议请讲解或租语音导览，历史瞬间会立体很多。"),
    "文艺":   ("慢下来文艺散步", "文艺散步", ["文艺","小众"], "留半天什么都不安排，随便钻巷子、喝咖啡、发呆，才是旅行的浪漫。"),
    "自然风光": ("把风景装进相机", "自然风光之旅", ["自然风光","治愈"], "看天气预报挑晴天出行，清晨和黄昏的光线最出片也最舒服。"),
    "摄影":   ("摄影爱好者出片指南", "摄影出片指南", ["摄影","出片"], "带三脚架拍夜景和水面倒影，广角拍大场景、长焦压缩远山层次。"),
    "打卡":   ("经典必打卡全集", "经典必打卡", ["打卡","热门"], "热门机位早点去避开人潮，错峰两小时体验感天差地别。"),
    "徒步":   ("徒步爱好者专线", "徒步专线", ["徒步","户外"], "穿防滑鞋、带登山杖，出发前查好天气和补给点，量力而行。"),
    "自驾":   ("一路向西自驾", "一路向西自驾", ["自驾","自由"], "提前规划加油/充电点和住宿，山区信号弱，离线地图务必下好。"),
    "小众":   ("避开人潮的小众秘境", "小众秘境", ["小众","秘境"], "小众地配套有限，吃饭住宿提前确认营业状态，尊重当地习俗。"),
    "海岛":   ("海岛度假慢时光", "海岛度假", ["海岛","度假"], "海边注意防晒和潮汐时间，浮潜选有救生员的区域更安全。"),
}

NICKNAMES = [
    ("小鹿旅行日记",2,"厦门","用脚步丈量世界，用镜头记录美好"),
    ("背包客阿明",1,"成都","一个人的背包，一段段未知的路"),
    ("家庭游达人妈妈",2,"杭州","带娃看世界，亲子游攻略分享"),
    ("摄影师老陈",1,"北京","风光摄影爱好者，记录中国最美瞬间"),
    ("吃货旅行家",1,"广州","走到哪吃到哪，美食就是我的旅行地图"),
    ("文艺少女小夏",2,"大理","在旅途中写诗，在风景里发呆"),
    ("环球旅行喵",2,"上海","一年去12个城市，目标是看遍世界"),
    ("山野徒步哥",1,"昆明","海拔5000以下的山都算热身"),
    ("地图美食官",1,"重庆","专治选择困难，帮你排好每一餐"),
    ("慢生活旅人",2,"苏州","不赶行程，只赶日落"),
    ("自驾游老司机",1,"西宁","方向盘一握，天下我有"),
    ("旅行手账君",2,"南京","把每一次出发都写成故事"),
]
MOCK_USERS = [f"mock-user-{i+1:03d}" for i in range(len(NICKNAMES))]

COMMENT_TPL = [
    "去过{city}！这篇攻略太实用了，已收藏📌",
    "楼主拍的照片绝了，请问{spot}需要提前预约吗？",
    "正打算去{city}，跟着抄作业了，谢谢分享～",
    "路线安排得很合理，收藏起来慢慢看",
    "求问{city}住宿有推荐吗？预算中等",
    "这个季节去{city}合适吗？会不会人很多",
    "太详细了，第一次去就有底气了",
    "同去过，补充一下{spot}傍晚去光线最好",
    "这预算含机票吗？还是只算当地花费",
    "已三连（点赞收藏评论），写得太好了",
    "请问带老人小孩适合这条线吗",
    "码住！下个月就出发🛫",
    "图二是哪个机位拍的？想要同款",
    "看完直接种草{city}了🌿",
    "细节满满，比那些水帖强太多了",
    "请问{city}当地交通方便吗？要租车吗",
    "收藏了，打算照着走一遍，有问题再请教",
    "攻略党狂喜，这种才是真干货",
    "想知道{spot}附近吃饭方便不",
    "博主更新太及时了，刚好用上",
    "路线密度刚好，不会一天赶三个城市",
    "已安利给闺蜜，一起去做作业",
    "求一个每日花费明细🙏",
    "这封面图也太好看了吧",
    "请问雨季去{city}影响大吗",
]
REPLY_TPL = [
    "对，建议提前在公众号约，现场经常排长队",
    "我当时住的老城区，走路就能到大部分景点",
    "不含机票哈，只算当地吃住行",
    "淡季去人少体验好，强烈推荐",
    "租个车会轻松很多，景点之间有点距离",
    "谢谢宝子，有问题随时来问～",
    "我去的也是这个季节，天气超给力",
]

WEATHERS = ["晴","多云","晴转多云","阴","小雨","阵雨转晴"]
CATEGORIES = ["自然风光","历史古迹","美食","文艺打卡","城市地标","亲子乐园"]
TRAVEL_MODES = ["drive","transit","hiking"]

def esc(s):
    return str(s).replace("'", "''")

def spot_category(name):
    if any(k in name for k in ["寺","庙","宫","陵","博物馆","窟","古城","关","祠","塔","墙","阁"]):
        return "历史古迹"
    if any(k in name for k in ["海","湖","山","谷","河","草原","林","岛","梯田","峰"]):
        return "自然风光"
    if any(k in name for k in ["街","巷","市","路","广场","村","垸","角"]):
        return "文艺打卡"
    return random.choice(CATEGORIES)

def build_route_chunks(spots, days):
    chunks, idx = [], 0
    for _ in range(days):
        n = random.randint(1, 3)
        chunk = spots[idx:idx+n] or [random.choice(spots)]
        chunks.append(chunk)
        idx += n
        if idx >= len(spots):
            idx = 0
    return chunks

def gen_users():
    rows = []
    for i, (name, gender, city, sig) in enumerate(NICKNAMES):
        uid = MOCK_USERS[i]
        avatar = f"https://picsum.photos/id/{64+i}/200/200"
        province = CITY_DATA[city][0] if city in CITY_DATA else "其他"
        created = (datetime(2026,1,1) + timedelta(days=random.randint(0,120))).strftime("%Y-%m-%d %H:%M:%S")
        rows.append(
            f"('{uid}','{esc(name)}','{avatar}',{gender},'{city}','{esc(sig)}','中国','{province}',"
            f"NULL,NULL,NULL,NULL,1,'{created}','{created}')"
        )
    return ("INSERT INTO `users` "
            "(`id`,`nickname`,`avatar`,`gender`,`city`,`signature`,`country`,`province`,"
            "`open_id`,`phone`,`password`,`preferences`,`status`,`created_at`,`updated_at`) VALUES\n"
            + ",\n".join(rows) + ";\n")

def gen_publishes(n=100):
    pub_rows, comment_rows = [], []
    plan_rows, day_rows, spot_rows = [], [], []
    cmt_seq = 1
    spot_seq = 1
    day_seq = 1
    for i in range(1, n+1):
        city = random.choice(list(CITY_DATA.keys()))
        province, cover_id, spots, city_themes, (base_lat, base_lng) = CITY_DATA[city]
        theme_key = random.choice(city_themes)
        label, suffix, extra_tags, tip = THEMES[theme_key]
        days = random.choice([2,3,3,4,4,5,5,6,7])
        nights = days - 1
        author = random.choice(MOCK_USERS)
        chunks = build_route_chunks(spots, days)
        route_lines = [f"📅 Day {d}（{city}）：{'、'.join(c)}" for d, c in enumerate(chunks, 1)]
        title = f"{city} {days}天{nights}晚｜{suffix}"
        desc = f"{city}{days}天{nights}晚{label}，路线+景点+实用贴士一次给齐，欢迎抄作业～"
        content = (f"刚从{city}回来，这份{label}亲测好用，分享给同样计划去的小伙伴。\n\n"
                   + "\n".join(route_lines) + "\n\n"
                   + f"💡 小贴士：{tip}")
        tags = json.dumps([city] + extra_tags, ensure_ascii=False)
        cover = f"https://picsum.photos/seed/mock{i}/600/800"
        view = random.randint(800, 9800)
        like = random.randint(28, 920)
        fav = random.randint(8, 420)
        created = (datetime(2026,5,1) + timedelta(days=random.randint(0,68), seconds=random.randint(0,86399))).strftime("%Y-%m-%d %H:%M:%S")
        pid = f"mock-pub-{i:03d}"
        plan_id = f"mock-plan-{i:03d}"

        # ---- 配套结构化路线 trip_plans / trip_days / trip_spots ----
        mode = random.choice(TRAVEL_MODES)
        start = datetime(2026,5,1) + timedelta(days=random.randint(0,60))
        end = start + timedelta(days=days-1)
        plan_rows.append(
            f"('{plan_id}','{esc(title)}','{city}','{start.strftime('%Y-%m-%d')}','{end.strftime('%Y-%m-%d')}',"
            f"'{esc(desc)}','completed','{author}',NULL,NULL,NULL,NULL,NULL,NULL,'{mode}',NULL,NULL,NULL,'{created}','{created}')"
        )
        for d, chunk in enumerate(chunks, 1):
            day_id = f"mock-day-{day_seq:04d}"
            day_seq += 1
            ddate = (start + timedelta(days=d-1)).strftime("%Y-%m-%d")
            weather = random.choice(WEATHERS)
            temp = f"{random.randint(12,32)}°C"
            hotel = f"{city}{random.choice(['五星','四星','精品','民宿'])}酒店"
            hotel_price = random.randint(200, 1280)
            day_rows.append(
                f"('{day_id}','{plan_id}',{d},'{ddate}','{weather}','{temp}','{esc(tip)}','{esc(hotel)}',NULL,{hotel_price},'{created}','{created}')"
            )
            arrival = 9 * 60 + random.randint(0,60)
            for oi, spot in enumerate(chunk, 1):
                spot_id = f"mock-spot-{spot_seq:04d}"
                spot_seq += 1
                lat = round(base_lat + ((spot_seq*7) % 11 - 5) * 0.006, 6)
                lng = round(base_lng + ((spot_seq*13) % 11 - 5) * 0.006, 6)
                arrival += random.randint(60, 150)
                dep = arrival + random.randint(60, 180)
                arr_str = f"{arrival//60:02d}:{arrival%60:02d}"
                dep_str = f"{dep//60:02d}:{dep%60:02d}"
                duration = f"{random.choice([1,1.5,2,2.5,3])}h"
                cost = f"¥{random.choice([0,0,30,50,80,120,160,200])}"
                cat = spot_category(spot)
                address = f"{province}{city}{spot}"
                guide = f"{spot}建议预留充足时间，避开人流高峰体验更佳；可结合周边一并游览。"
                tip_txt = f"【{spot}】{random.choice(['早到避开团队客','带好水和干粮','穿舒适走路鞋','门票可线上提前买','注意防晒','当地小吃别错过'])}"
                image = f"https://picsum.photos/seed/{esc(spot)}{spot_seq}/600/400"
                spot_rows.append(
                    f"('{spot_id}','{day_id}','{esc(spot)}','{cat}','{esc(address)}',{lat},{lng},{oi},"
                    f"'{arr_str}','{dep_str}','{duration}','{cost}','{esc(tip_txt)}','{esc(guide)}',NULL,'{image}',0,NULL,'{created}','{created}')"
                )

        # ---- 评论 ----
        n_cmt = random.randint(2, 5)
        top_ids, this_cmt = [], 0
        for _ in range(n_cmt):
            cuid = random.choice([u for u in MOCK_USERS if u != author] or MOCK_USERS)
            ctext = random.choice(COMMENT_TPL).format(city=city, spot=random.choice(spots)).replace("'","''")
            ccreated = (datetime.strptime(created, "%Y-%m-%d %H:%M:%S") + timedelta(hours=random.randint(1,72))).strftime("%Y-%m-%d %H:%M:%S")
            cid = f"mock-cmt-{cmt_seq:04d}"
            cmt_seq += 1
            top_ids.append(cid)
            comment_rows.append(f"('{cid}','{pid}','{cuid}',NULL,'{ctext}',{random.randint(0,46)},1,NULL,0,'{ccreated}','{ccreated}')")
            this_cmt += 1
        for _ in range(random.randint(0, 2)):
            if not top_ids:
                break
            parent = random.choice(top_ids)
            ruid = random.choice(MOCK_USERS)
            rtext = random.choice(REPLY_TPL).replace("'","''")
            rcreated = (datetime.strptime(created, "%Y-%m-%d %H:%M:%S") + timedelta(hours=random.randint(73,160))).strftime("%Y-%m-%d %H:%M:%S")
            cid = f"mock-cmt-{cmt_seq:04d}"
            cmt_seq += 1
            comment_rows.append(f"('{cid}','{pid}','{ruid}','{parent}','{rtext}',{random.randint(0,12)},1,NULL,0,'{rcreated}','{rcreated}')")
            this_cmt += 1

        # ---- 发布行（带 trip_plan_id）----
        pub_rows.append(
            f"('{pid}','{author}','{plan_id}','{esc(title)}','{esc(content)}','{esc(desc)}','{cover}','{city}',"
            f"{days},{nights},'{tags}',1,{view},{like},{this_cmt},{fav},1,NULL,0,'{created}','{created}')"
        )

    pub_sql = ("INSERT INTO `trip_publish` "
        "(`id`,`user_id`,`trip_plan_id`,`title`,`content`,`description`,`cover_image`,`location`,`days`,`nights`,`tags`,`status`,`view_count`,`like_count`,`comment_count`,`fav_count`,`review_status`,`review_reason`,`deleted`,`created_at`,`updated_at`) VALUES\n"
        + ",\n".join(pub_rows) + ";\n")
    cmt_sql = ("INSERT INTO `comment` "
        "(`id`,`publish_id`,`user_id`,`parent_id`,`content`,`like_count`,`review_status`,`review_reason`,`deleted`,`created_at`,`updated_at`) VALUES\n"
        + ",\n".join(comment_rows) + ";\n")
    plan_sql = ("INSERT INTO `trip_plans` "
        "(`id`,`name`,`destination`,`start_date`,`end_date`,`description`,`status`,`user_id`,`preferences`,`must_visit_places`,`budget`,`total_distance`,`start_point`,`end_point`,`travel_mode`,`routes_json`,`versions_json`,`packing_list_json`,`created_at`,`updated_at`) VALUES\n"
        + ",\n".join(plan_rows) + ";\n")
    day_sql = ("INSERT INTO `trip_days` "
        "(`id`,`trip_id`,`day`,`date`,`weather`,`temperature`,`notes`,`hotel_name`,`hotel_detail`,`hotel_price`,`created_at`,`updated_at`) VALUES\n"
        + ",\n".join(day_rows) + ";\n")
    spot_sql = ("INSERT INTO `trip_spots` "
        "(`id`,`trip_day_id`,`name`,`category`,`address`,`latitude`,`longitude`,`order_num`,`arrival_time`,`departure_time`,`duration`,`cost`,`tips`,`travel_guide`,`temperature`,`image`,`is_reached`,`reached_at`,`created_at`,`updated_at`) VALUES\n"
        + ",\n".join(spot_rows) + ";\n")
    return pub_sql, cmt_sql, plan_sql, day_sql, spot_sql

def gen_follows(n=50):
    rows, seen = [], set()
    while len(rows) < n and len(seen) < n*5:
        a, b = random.sample(MOCK_USERS, 2)
        if (a, b) in seen:
            continue
        seen.add((a, b))
        created = (datetime(2026,3,1) + timedelta(days=random.randint(0,120))).strftime("%Y-%m-%d %H:%M:%S")
        rows.append(f"('mock-follow-{len(rows)+1:03d}','{a}','{b}','{created}')")
    return ("INSERT INTO `follow_relation` (`id`,`follower_id`,`following_id`,`created_at`) VALUES\n"
            + ",\n".join(rows) + ";\n")

# ---------------------------------------------------------------------------
header = """-- ============================================================
-- 🏕️ 拾路派 (PathFinder) — 发现/广场 模拟数据 (100 条)
-- 生成时间：2026-07-09
-- 说明：每次执行先按 mock- 前缀清理旧数据再重新插入（幂等、可重复执行）；
--       id 全部带 mock- 前缀，不会与已有 seed-user / seed-pub 数据冲突。
--       包含：12 用户 + 100 发布(含路线) + 配套 trip_plans/trip_days/trip_spots
--             + ~460 评论 + 50 关注。
--       详情页(trip-detail)的路线时间线/地图由 trip_plan_id 关联拉取。
-- ============================================================

USE `travel_app`;

"""

pub_sql, cmt_sql, plan_sql, day_sql, spot_sql = gen_publishes(100)

# 🧹 清理旧 mock 数据（重复执行时用）：先按 mock- 前缀删除，再重新插入。
# 关键：避免 INSERT IGNORE 跳过已存在的 trip_publish 行，导致其 trip_plan_id 一直为
# NULL，进而详情页 buildDayList(null) 返回空、路线区域空白。
cleanup = """-- 🧹 清理旧 mock 数据（按依赖逆序，可重复执行）
DELETE FROM `comment` WHERE `id` LIKE 'mock-cmt-%';
DELETE FROM `trip_spots` WHERE `trip_day_id` LIKE 'mock-day-%';
DELETE FROM `trip_days` WHERE `trip_id` LIKE 'mock-plan-%';
DELETE FROM `follow_relation` WHERE `id` LIKE 'mock-follow-%';
DELETE FROM `trip_publish` WHERE `id` LIKE 'mock-pub-%' OR `trip_plan_id` LIKE 'mock-plan-%';
DELETE FROM `trip_plans` WHERE `id` LIKE 'mock-plan-%';
DELETE FROM `users` WHERE `id` LIKE 'mock-user-%';

"""

sql = (header
       + cleanup
       + "-- ══════ 1. 模拟用户 (12) ══════\n" + gen_users() + "\n"
       + "-- ══════ 2. 行程计划 trip_plans (100) ══════\n" + plan_sql + "\n"
       + "-- ══════ 3. 行程天 trip_days ══════\n" + day_sql + "\n"
       + "-- ══════ 4. 行程景点 trip_spots (路线时间线/地图) ══════\n" + spot_sql + "\n"
       + "-- ══════ 5. 广场发布行程 trip_publish (100, 含路线) ══════\n" + pub_sql + "\n"
       + "-- ══════ 6. 评论 (含楼中楼回复) ══════\n" + cmt_sql + "\n"
       + "-- ══════ 7. 关注关系 (50) ══════\n" + gen_follows(50) + "\n"
       + "-- ══════ 验证 ══════\n"
       + "SELECT 'mock 数据插入完成' AS result,\n"
       + "  (SELECT COUNT(*) FROM users WHERE id LIKE 'mock-user-%') AS users,\n"
       + "  (SELECT COUNT(*) FROM trip_plans WHERE id LIKE 'mock-plan-%') AS plans,\n"
       + "  (SELECT COUNT(*) FROM trip_days WHERE id LIKE 'mock-day-%') AS days,\n"
       + "  (SELECT COUNT(*) FROM trip_spots WHERE id LIKE 'mock-spot-%') AS spots,\n"
       + "  (SELECT COUNT(*) FROM trip_publish WHERE id LIKE 'mock-pub-%') AS publishes,\n"
       + "  (SELECT COUNT(*) FROM comment WHERE id LIKE 'mock-cmt-%') AS comments,\n"
       + "  (SELECT COUNT(*) FROM follow_relation WHERE id LIKE 'mock-follow-%') AS follows;\n")

with open(r"C:/Users/14143/Desktop/AI 旅游/sql/seed-discover-100.sql", "w", encoding="utf-8") as f:
    f.write(sql)

print("OK, 文件已生成: sql/seed-discover-100.sql")
print("plans:", sql.count("mock-plan-"), "days 段:", day_sql.count("mock-day-"),
      "spots:", spot_sql.count("mock-spot-"), "pub:", pub_sql.count("mock-pub-"),
      "comments:", cmt_sql.count("mock-cmt-"))
