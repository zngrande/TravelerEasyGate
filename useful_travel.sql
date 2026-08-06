-- phpMyAdmin SQL Dump
-- version 4.8.4
-- https://www.phpmyadmin.net/
--
-- 主機: 127.0.0.1
-- 產生時間： 
-- 伺服器版本: 10.1.37-MariaDB
-- PHP 版本： 7.2.14

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- 資料庫： `useful_travel`
--

-- --------------------------------------------------------

--
-- 資料表結構 `agency`
--

CREATE TABLE `agency` (
  `AID` int(11) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `license_no` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `plan_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'trial',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `agency`
--

INSERT INTO `agency` (`AID`, `name`, `license_no`, `contact_phone`, `contact_email`, `plan_type`, `created_at`) VALUES
(1, '測試', '', '0938678687', 'test', 'trial', '2026-08-06 09:28:20');

-- --------------------------------------------------------

--
-- 資料表結構 `ai_import`
--

CREATE TABLE `ai_import` (
  `IPID` int(11) NOT NULL,
  `AID` int(11) NOT NULL,
  `created_by` int(11) NOT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'text',
  `raw_content` mediumtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_itinerary_id` int(11) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `ai_import`
--

INSERT INTO `ai_import` (`IPID`, `AID`, `created_by`, `source_type`, `raw_content`, `status`, `error_message`, `result_itinerary_id`, `created_at`) VALUES
(1, 1, 1, 'pdf', '【冰雪秘境】日本東北藏王樹冰絕景、銀山溫泉 \r\n大內宿、會津若松城、只見線、藏王樹冰纜車、新幹線子彈列車、採果體驗 \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n參考航班 \r\n去程 長榮航空（BR108）高雄小港機場／成田國際機場 0700/1125(預定) \r\n回程 長榮航空（BR107）成田國際機場／高雄小港機場 1225/1545(預定) \r\n \r\n每  日  行  程  內  容 \r\n \r\n第一天 \r\n高雄小港國際機場/東京成田國際機場 \r\n－冬季限定!體驗採草莓吃到飽－裏盤梯或豬苗代湖溫泉 \r\n集合於「小港國際機場」，由專人協辦出境手續後，搭乘豪華噴射客機飛往日本「東京」。 \r\n東京是日本的首都，更是日本政治、經濟、文化的中心。在全球各大城市裡，東京應該算是最適\r\n合自由行的精彩城市。這人口高達 1,300 萬人的國際大都會，同時集合了最新的流行時尚、各國\r\n的特色美食、刺激的主題樂園以及江戶氣息的傳統文化，多采多姿的時尚都會令人百玩不厭！ \r\n採草莓體驗－雪中採草莓食放題 \r\n在生機農園站著採草莓真的很新奇，每人發一把剪刀丶一個空盒...剪刀是用來剪草莓的，空盒是放\r\n吃剩的草莓蒂，這草苺又香又甜，每一顆都像紅寶石閃閃動人。裝著滿滿的草莓與滿滿的幸福 \r\n※若遇草莓園休園或塞車時間關係無法採果則改贈每人草莓一盒、敬請理解。 \r\n會津地區 \r\n福島縣幅員遼闊，自然資源豐富。可以飽覽入選「日本百大名山」的「磐梯山」、被譽為日本最\r\n佳自然景觀之一的「裏磐梯」，以及有名的賞花勝地「花見山」等美麗壯觀的大自然。此外，眾\r\n多著名的溫泉鄉也是福島縣吸引遊客的一大魅力。建議造訪福島縣，體驗在風景優美的環境中享\r\n受泡溫泉的極致樂趣，來一趟投身於大自然懷抱的療癒之旅。 \r\n餐 食 住 宿 \r\n早餐／機上套餐 \r\n午餐／特別安排日式壽司餐盒+日本茶 \r\n晚餐／迎賓和洋式自助餐 或 溫泉會席料理 \r\n豬苗代湖 LISTEL 或 裏盤梯 LAKE RESORT 或  \r\n裏盤梯美居 或 裏盤梯星野 或同級 \r\n \r\n第二天 \r\n日本三大茅草合掌造部落之一～大內宿 \r\n－遠眺世界最美鐵道．只見線～第一橋樑展望台 \r\n－日本第四大湖～豬苗代湖(觀賞水花冰奇觀) \r\n－白雪鶴之城~會津若松城（不上天守閣） \r\n大內宿－日本三大合掌村聚落之一 \r\n是以會津西街道車站為中心而形成的村落，在明治初期沿大川開闢的國道通車為止，這裡是個相\r\n當繁榮的城鎮。沿著大內宿街道走，仍可看到約四十間的茅草屋保留著當時的模樣。這個地區已\r\n被指定為國家重要傳統建築物保存地區，大內宿也完整保存了昔日村落的古樸景致，讓來到這裡\r\n的旅人們，走在這安靜的街道上，也能感受時光倒流的懷舊氣息。 \r\n第一只見川橋梁展望台－只見線最代表性拍攝景點、絕美冬景色 \r\n穿梭在只見川間的 JR 只見線沿線可以見到許多雄壯無比的景觀，在不同的季節會呈現出不同的樣\r\n貌。JR 只見線在 2008 年被選為「沿線紅葉最美麗的鐵道路線」排行榜第一名。 \r\n會津若松城－日本百大名城之一，又名『鶴城』 \r\n福島縣最著名的古城，最早建於西元 1384 年，迄今有六百多年歷史，此城天守閣之造形有如展\r\n開兩翼在空中飛舞的白鶴，姿態優美，因此有『鶴城』之美稱。此城是日本近代有名的歷史舞\r\n台，1868 年 10 月發生的戊辰戰爭中，維新政府軍來到會津若松城下，與代表舊幕府勢力的會津\r\n藩士展開決戰。一群年僅 15、16 歲的少年被編成白虎隊奮勇迎敵，彈盡糧絕之際他們感嘆守城\r\n淪陷在即，於是 20 名隊員全體切腹自盡，留下令人唏噓的故事。 \r\n豬苗代湖－日本第四大湖 \r\n豬苗代湖位於福島縣近中央，磐梯朝日國立公園的外入口處。此湖被稱爲日本第四大湖，猶如天\r\n鏡把磐梯山的英姿映照在湖面上，因而也被稱作『天鏡湖』。每年春秋兩季是該湖最美的時節，\r\n而 12 月左右會有成群的白鳥飛來此避寒，直至春天才離去，景象十分壯觀。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／手打蕎麥御膳 或 日式鍋物料理 \r\n晚餐／日式涮涮鍋食放題+軟式飲料暢飲 或 飯\r\n店內用迎賓和洋式自助餐 或 溫泉會席料理 \r\n天童瀧之湯 或 榮屋 或 美味求真之宿 或 秋保溫\r\n泉 Crescent Resort 或 藏王美居水療渡假村仙\r\n台京阪 或 蒙特利 或 大都會 或同級 \r\n \r\n第三天 \r\n東北神秘的藏王樹冰世界～藏王纜車(藏王山麓站++地蔵山頂駅) \r\n特別安排【雪盆戲雪】～滑雪盆、堆雪人、打雪仗 \r\n～登上藏王高原欣賞大自然的藝術品樹冰 \r\n－大正浪漫日本風情～銀山溫泉街雪中散策及足湯体驗 \r\n藏王樹冰－冬季遊日必訪景點之一、帶您一窺東北神秘的藏王樹冰 \r\n東北冬季獨特的名所－藏王高原，自古名聞遐邇，為奧羽地方之秘境。搭乘纜車登上藏王山上欣\r\n賞世界特殊自然雪景「樹冰」，藏王的樹冰是冬季季風所生成的自然藝術。來自西伯利亞寒冷季\r\n節風遇到藏王朝日連峰，急速上昇，形成飽含濕氣的冷霧，在青森冷杉的枝幹上瞬間結冰，再加\r\n上雪花覆蓋，於是形成巨大的塊狀物。從前當地的獵人稱樹冰為雪的幽靈，一棵棵佇立的樹冰，\r\n是藏王著名的奇景，近年來吸引了不少國際觀光客造訪，身處在亞熱帶的我們，無法想像的美\r\n景，邀您一起親身去探尋這大自然偉大的藝術品。特別安排【雪盆戲雪】～滑雪盆、堆雪人、打\r\n雪仗 \r\n【特別說明】藏王樹冰纜車 \r\n※樹冰為特殊自然景緻，如因天候、風雪之影響，導致樹冰尚未形成或提前結束或樹冰期已過，仍會搭乘纜車上\r\n山眺望美景，不另退費，敬請了解。 \r\n※如遇藏王纜車因自然天候、設備檢修、或預約情況等其他不可抗力因素之影響導致：只搭乘第一段纜車時，每\r\n人退費￥1,500 日幣(不含嬰兒)，隨後專車前往下一景點。 \r\n②全程無法搭乘時，將改為前往「藏王中央纜車」或「藏王狐狸村」並退費每人￥2,000 日幣(不含嬰兒)，敬請\r\n了解。 \r\n銀山溫泉街－保留著大正時代風情的夢幻旅遊景點 \r\n 因為一部電視劇阿信而聞名於世。原只是銀礦礦工消除疲勞的著名溫泉，現在還殘留者大正羅谩\r\n氣息。銀山溫泉是由江戶時代時非常繁榮的「延澤銀山」而來，而被稱為溫泉街則是由後來興建\r\n的旅館直接將銀山川自然湧出的源泉當作內湯使用後開始且變得更熱鬧。在江戶時代，延澤銀山\r\n與大盛銀山、生野銀山並列為三大銀山。現在的延澤銀山雖已關閉，但在寬永年間左右是最興盛\r\n的時期，當時在銀山工作的人口已超過兩萬人。現在的銀山溫泉街上，沿著銀山川溪谷兩岸，有\r\n成排的 3、4 層樓木結構的旅館，氣氛寧靜，宛如世外桃源，人們來到這裏總會産生一種錯覺，以\r\n爲到了拍攝日本古代戲的電影村。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／藏王風味定食 或 發放代金￥3000 自理 \r\n晚餐／日式涮涮鍋食放題+軟式飲料暢飲 \r\n仙台蒙特利 或 京阪 或 大都會 或 國際 或 \r\nJALCITY 或 HILLS 或同級 \r\n \r\n第四天 \r\n日本三景之一～松島(搭乘遊覽船) 、五大堂 \r\n※特別加贈 烤魚板+仙台名菓『萩之月』奶油蛋糕 \r\n－奧州第一宮~鹽竈神社－免稅店 \r\n－仙台城跡（青葉城） \r\n－體驗世界第一日本新幹線子彈列車(仙台+++東京)－成田 \r\n松島－日本三景之一，太平洋的海平線與大小島嶼交織而成的絕美景色 \r\n散佈在宮城縣中部、松島灣沿岸以及松島灣上的 260 個大小島嶼組成的島嶼群的總稱。松島的景\r\n色根據地點與季節產生各種變化，景色之優美堪稱日本三景之首。風平浪靜的松島灣上浮起一個\r\n又一個小島，黑松和紅松挺立在灰白色的岩石上。松島的所有小島中，扇谷、富山、大鷹森和多\r\n聞山 4 處的周圍景色被稱為「松島四大觀」，因站在島上可以欣賞松島的各種不同神態而聞名，\r\n一年四季遊客絡繹不絕。 \r\n松島 五大堂 \r\n在東北地區具有千年的歷史，木造屋頂為單層造形，透露出歷史的刻痕，現成為日本重要文化\r\n財，並列為文化保護材，五大堂這座吸引了眾多遊客的建築物位於五大堂島上。它是政宗於 1609\r\n年再建的。五大明王像被供奉在堂中。五大堂的五字即由此而來。五大堂內的頂部繪有中國的十\r\n二生肖之像。這裡只在每三十三年舉行一次的特殊儀式時才向公眾開放。 \r\n仙台城跡 \r\n由伊達政宗所築城的仙台城的遺跡。由穿著盔甲，雄姿威武的「伊達武將隊」迎接遊客的到來!仙\r\n台城跡為伊達政宗家族的根據地。在主要的遺跡天守台之前，豎立著伊達政宗公全副武裝騎馬的\r\n銅像。從這裡可以一覽仙台全貌，清晨的海上日出與夜景更是別有一番風味。 \r\n鹽竈神社 \r\n主要供奉的神祇為「鹽土老翁神」，據說這位神明教導人們運用海水製鹽的方法，也正是鹽釜此\r\n一地名之由來。在這可以祈求各式各樣的願望聞名，舉凡海上安全、漁獲豐收、武運長久、安\r\n胎、交通安全、必勝、成功等等，都可以來鹽釜神社祈求。 \r\n免稅店－參觀選購各項具特色的商品 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／松島名物牛舌、牡蠣風味御膳 \r\n晚餐／日式燒肉吃到飽 或 日式風味御膳  \r\n      或 飯店內用和洋式自助餐 \r\n幕張新大谷 或 幕張 APA 或 成田馬洛德 或 ANA \r\nCROWAN PLAZA 或 MYSTAYS 或同級 \r\n \r\n第五天 東京成田國際機場/高雄小港國際機場 \r\n早餐後，前往＜成田國際空港＞，帶著愉快心情、滿足笑容、以及滿滿的回憶，告別難忘的日本\r\n之旅，搭乘豪華客機返回甜蜜的家。【成田國際機場免稅店】日本最大機場備有非常多的免税店\r\n和名店，旅客可以盡情購買由日本人氣糖果到世界高級品牌等多款商品。購買菸酒類和化妝品等\r\n免稅品，請盡情享受旅程最後一站的優惠購物之旅。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／機上套餐 \r\n溫暖的家 \r\n \r\n【注意事項】 \r\n1.本行程按航空公司之規定需「團進團出」，不可延回、不得退票及延期使用。 \r\n2.班機時間以航空公司最後奉核時間為準。 \r\n3.住宿飯店及行程先後秩序以說明會資料為主。如遇交通機關、道路狀況或氣候等不可抗拒因素導\r\n致行程必須臨時變動，本公司保有行程調整權，請以當地導遊安排順序為準；敬請見諒。 \r\n \r\n', 'failed', 'Claude API 呼叫失敗 (HTTP 400): {\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.\"},\"request_id\":\"req_011Cdm3oEXhcwizf5C9Czkim\"}', NULL, '2026-08-06 14:49:18'),
(2, 1, 1, 'pdf', '【冰雪秘境】日本東北藏王樹冰絕景、銀山溫泉 \r\n大內宿、會津若松城、只見線、藏王樹冰纜車、新幹線子彈列車、採果體驗 \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n \r\n參考航班 \r\n去程 長榮航空（BR108）高雄小港機場／成田國際機場 0700/1125(預定) \r\n回程 長榮航空（BR107）成田國際機場／高雄小港機場 1225/1545(預定) \r\n \r\n每  日  行  程  內  容 \r\n \r\n第一天 \r\n高雄小港國際機場/東京成田國際機場 \r\n－冬季限定!體驗採草莓吃到飽－裏盤梯或豬苗代湖溫泉 \r\n集合於「小港國際機場」，由專人協辦出境手續後，搭乘豪華噴射客機飛往日本「東京」。 \r\n東京是日本的首都，更是日本政治、經濟、文化的中心。在全球各大城市裡，東京應該算是最適\r\n合自由行的精彩城市。這人口高達 1,300 萬人的國際大都會，同時集合了最新的流行時尚、各國\r\n的特色美食、刺激的主題樂園以及江戶氣息的傳統文化，多采多姿的時尚都會令人百玩不厭！ \r\n採草莓體驗－雪中採草莓食放題 \r\n在生機農園站著採草莓真的很新奇，每人發一把剪刀丶一個空盒...剪刀是用來剪草莓的，空盒是放\r\n吃剩的草莓蒂，這草苺又香又甜，每一顆都像紅寶石閃閃動人。裝著滿滿的草莓與滿滿的幸福 \r\n※若遇草莓園休園或塞車時間關係無法採果則改贈每人草莓一盒、敬請理解。 \r\n會津地區 \r\n福島縣幅員遼闊，自然資源豐富。可以飽覽入選「日本百大名山」的「磐梯山」、被譽為日本最\r\n佳自然景觀之一的「裏磐梯」，以及有名的賞花勝地「花見山」等美麗壯觀的大自然。此外，眾\r\n多著名的溫泉鄉也是福島縣吸引遊客的一大魅力。建議造訪福島縣，體驗在風景優美的環境中享\r\n受泡溫泉的極致樂趣，來一趟投身於大自然懷抱的療癒之旅。 \r\n餐 食 住 宿 \r\n早餐／機上套餐 \r\n午餐／特別安排日式壽司餐盒+日本茶 \r\n晚餐／迎賓和洋式自助餐 或 溫泉會席料理 \r\n豬苗代湖 LISTEL 或 裏盤梯 LAKE RESORT 或  \r\n裏盤梯美居 或 裏盤梯星野 或同級 \r\n \r\n第二天 \r\n日本三大茅草合掌造部落之一～大內宿 \r\n－遠眺世界最美鐵道．只見線～第一橋樑展望台 \r\n－日本第四大湖～豬苗代湖(觀賞水花冰奇觀) \r\n－白雪鶴之城~會津若松城（不上天守閣） \r\n大內宿－日本三大合掌村聚落之一 \r\n是以會津西街道車站為中心而形成的村落，在明治初期沿大川開闢的國道通車為止，這裡是個相\r\n當繁榮的城鎮。沿著大內宿街道走，仍可看到約四十間的茅草屋保留著當時的模樣。這個地區已\r\n被指定為國家重要傳統建築物保存地區，大內宿也完整保存了昔日村落的古樸景致，讓來到這裡\r\n的旅人們，走在這安靜的街道上，也能感受時光倒流的懷舊氣息。 \r\n第一只見川橋梁展望台－只見線最代表性拍攝景點、絕美冬景色 \r\n穿梭在只見川間的 JR 只見線沿線可以見到許多雄壯無比的景觀，在不同的季節會呈現出不同的樣\r\n貌。JR 只見線在 2008 年被選為「沿線紅葉最美麗的鐵道路線」排行榜第一名。 \r\n會津若松城－日本百大名城之一，又名『鶴城』 \r\n福島縣最著名的古城，最早建於西元 1384 年，迄今有六百多年歷史，此城天守閣之造形有如展\r\n開兩翼在空中飛舞的白鶴，姿態優美，因此有『鶴城』之美稱。此城是日本近代有名的歷史舞\r\n台，1868 年 10 月發生的戊辰戰爭中，維新政府軍來到會津若松城下，與代表舊幕府勢力的會津\r\n藩士展開決戰。一群年僅 15、16 歲的少年被編成白虎隊奮勇迎敵，彈盡糧絕之際他們感嘆守城\r\n淪陷在即，於是 20 名隊員全體切腹自盡，留下令人唏噓的故事。 \r\n豬苗代湖－日本第四大湖 \r\n豬苗代湖位於福島縣近中央，磐梯朝日國立公園的外入口處。此湖被稱爲日本第四大湖，猶如天\r\n鏡把磐梯山的英姿映照在湖面上，因而也被稱作『天鏡湖』。每年春秋兩季是該湖最美的時節，\r\n而 12 月左右會有成群的白鳥飛來此避寒，直至春天才離去，景象十分壯觀。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／手打蕎麥御膳 或 日式鍋物料理 \r\n晚餐／日式涮涮鍋食放題+軟式飲料暢飲 或 飯\r\n店內用迎賓和洋式自助餐 或 溫泉會席料理 \r\n天童瀧之湯 或 榮屋 或 美味求真之宿 或 秋保溫\r\n泉 Crescent Resort 或 藏王美居水療渡假村仙\r\n台京阪 或 蒙特利 或 大都會 或同級 \r\n \r\n第三天 \r\n東北神秘的藏王樹冰世界～藏王纜車(藏王山麓站++地蔵山頂駅) \r\n特別安排【雪盆戲雪】～滑雪盆、堆雪人、打雪仗 \r\n～登上藏王高原欣賞大自然的藝術品樹冰 \r\n－大正浪漫日本風情～銀山溫泉街雪中散策及足湯体驗 \r\n藏王樹冰－冬季遊日必訪景點之一、帶您一窺東北神秘的藏王樹冰 \r\n東北冬季獨特的名所－藏王高原，自古名聞遐邇，為奧羽地方之秘境。搭乘纜車登上藏王山上欣\r\n賞世界特殊自然雪景「樹冰」，藏王的樹冰是冬季季風所生成的自然藝術。來自西伯利亞寒冷季\r\n節風遇到藏王朝日連峰，急速上昇，形成飽含濕氣的冷霧，在青森冷杉的枝幹上瞬間結冰，再加\r\n上雪花覆蓋，於是形成巨大的塊狀物。從前當地的獵人稱樹冰為雪的幽靈，一棵棵佇立的樹冰，\r\n是藏王著名的奇景，近年來吸引了不少國際觀光客造訪，身處在亞熱帶的我們，無法想像的美\r\n景，邀您一起親身去探尋這大自然偉大的藝術品。特別安排【雪盆戲雪】～滑雪盆、堆雪人、打\r\n雪仗 \r\n【特別說明】藏王樹冰纜車 \r\n※樹冰為特殊自然景緻，如因天候、風雪之影響，導致樹冰尚未形成或提前結束或樹冰期已過，仍會搭乘纜車上\r\n山眺望美景，不另退費，敬請了解。 \r\n※如遇藏王纜車因自然天候、設備檢修、或預約情況等其他不可抗力因素之影響導致：只搭乘第一段纜車時，每\r\n人退費￥1,500 日幣(不含嬰兒)，隨後專車前往下一景點。 \r\n②全程無法搭乘時，將改為前往「藏王中央纜車」或「藏王狐狸村」並退費每人￥2,000 日幣(不含嬰兒)，敬請\r\n了解。 \r\n銀山溫泉街－保留著大正時代風情的夢幻旅遊景點 \r\n 因為一部電視劇阿信而聞名於世。原只是銀礦礦工消除疲勞的著名溫泉，現在還殘留者大正羅谩\r\n氣息。銀山溫泉是由江戶時代時非常繁榮的「延澤銀山」而來，而被稱為溫泉街則是由後來興建\r\n的旅館直接將銀山川自然湧出的源泉當作內湯使用後開始且變得更熱鬧。在江戶時代，延澤銀山\r\n與大盛銀山、生野銀山並列為三大銀山。現在的延澤銀山雖已關閉，但在寬永年間左右是最興盛\r\n的時期，當時在銀山工作的人口已超過兩萬人。現在的銀山溫泉街上，沿著銀山川溪谷兩岸，有\r\n成排的 3、4 層樓木結構的旅館，氣氛寧靜，宛如世外桃源，人們來到這裏總會産生一種錯覺，以\r\n爲到了拍攝日本古代戲的電影村。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／藏王風味定食 或 發放代金￥3000 自理 \r\n晚餐／日式涮涮鍋食放題+軟式飲料暢飲 \r\n仙台蒙特利 或 京阪 或 大都會 或 國際 或 \r\nJALCITY 或 HILLS 或同級 \r\n \r\n第四天 \r\n日本三景之一～松島(搭乘遊覽船) 、五大堂 \r\n※特別加贈 烤魚板+仙台名菓『萩之月』奶油蛋糕 \r\n－奧州第一宮~鹽竈神社－免稅店 \r\n－仙台城跡（青葉城） \r\n－體驗世界第一日本新幹線子彈列車(仙台+++東京)－成田 \r\n松島－日本三景之一，太平洋的海平線與大小島嶼交織而成的絕美景色 \r\n散佈在宮城縣中部、松島灣沿岸以及松島灣上的 260 個大小島嶼組成的島嶼群的總稱。松島的景\r\n色根據地點與季節產生各種變化，景色之優美堪稱日本三景之首。風平浪靜的松島灣上浮起一個\r\n又一個小島，黑松和紅松挺立在灰白色的岩石上。松島的所有小島中，扇谷、富山、大鷹森和多\r\n聞山 4 處的周圍景色被稱為「松島四大觀」，因站在島上可以欣賞松島的各種不同神態而聞名，\r\n一年四季遊客絡繹不絕。 \r\n松島 五大堂 \r\n在東北地區具有千年的歷史，木造屋頂為單層造形，透露出歷史的刻痕，現成為日本重要文化\r\n財，並列為文化保護材，五大堂這座吸引了眾多遊客的建築物位於五大堂島上。它是政宗於 1609\r\n年再建的。五大明王像被供奉在堂中。五大堂的五字即由此而來。五大堂內的頂部繪有中國的十\r\n二生肖之像。這裡只在每三十三年舉行一次的特殊儀式時才向公眾開放。 \r\n仙台城跡 \r\n由伊達政宗所築城的仙台城的遺跡。由穿著盔甲，雄姿威武的「伊達武將隊」迎接遊客的到來!仙\r\n台城跡為伊達政宗家族的根據地。在主要的遺跡天守台之前，豎立著伊達政宗公全副武裝騎馬的\r\n銅像。從這裡可以一覽仙台全貌，清晨的海上日出與夜景更是別有一番風味。 \r\n鹽竈神社 \r\n主要供奉的神祇為「鹽土老翁神」，據說這位神明教導人們運用海水製鹽的方法，也正是鹽釜此\r\n一地名之由來。在這可以祈求各式各樣的願望聞名，舉凡海上安全、漁獲豐收、武運長久、安\r\n胎、交通安全、必勝、成功等等，都可以來鹽釜神社祈求。 \r\n免稅店－參觀選購各項具特色的商品 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／松島名物牛舌、牡蠣風味御膳 \r\n晚餐／日式燒肉吃到飽 或 日式風味御膳  \r\n      或 飯店內用和洋式自助餐 \r\n幕張新大谷 或 幕張 APA 或 成田馬洛德 或 ANA \r\nCROWAN PLAZA 或 MYSTAYS 或同級 \r\n \r\n第五天 東京成田國際機場/高雄小港國際機場 \r\n早餐後，前往＜成田國際空港＞，帶著愉快心情、滿足笑容、以及滿滿的回憶，告別難忘的日本\r\n之旅，搭乘豪華客機返回甜蜜的家。【成田國際機場免稅店】日本最大機場備有非常多的免税店\r\n和名店，旅客可以盡情購買由日本人氣糖果到世界高級品牌等多款商品。購買菸酒類和化妝品等\r\n免稅品，請盡情享受旅程最後一站的優惠購物之旅。 \r\n餐 食 住 宿 \r\n早餐／飯店內享用 \r\n午餐／機上套餐 \r\n溫暖的家 \r\n \r\n【注意事項】 \r\n1.本行程按航空公司之規定需「團進團出」，不可延回、不得退票及延期使用。 \r\n2.班機時間以航空公司最後奉核時間為準。 \r\n3.住宿飯店及行程先後秩序以說明會資料為主。如遇交通機關、道路狀況或氣候等不可抗拒因素導\r\n致行程必須臨時變動，本公司保有行程調整權，請以當地導遊安排順序為準；敬請見諒。 \r\n \r\n', 'confirmed', NULL, 3, '2026-08-06 14:59:41'),
(3, 1, 1, 'docx', '【四國秘境全覽】小豆島跳島溫泉美食五日\n\n\n\n\n參考航班	去程	中華航空（CI7760）高雄小港機場／高松機場	0635/1030(預定)\n	回程	中華航空（CI7761）高松機場／高雄小港機場	1130/1335(預定)\n參考航班\n(10/26起預定)	去程	中華航空（CI7760）高雄小港機場／高松機場	0655/1035(預定)\n	回程	中華航空（CI7761）高松機場／高雄小港機場	1135/1405(預定)\n\n\n第一天	高雄小港國際機場/高松空港	－日本三大名園之一～岡山後樂園	－行經瀨戶內海(島波海道)~多多羅大橋~來島大橋	－神隱少女湯屋～道後溫泉街散策	－一同來尋找湯婆婆及千尋的身影～少爺音樂鐘～放生園足湯\n今天集合於高雄國際機場，由專業熱心的領隊辦理登機手續，搭程客機直飛四國高松機場。	【岡山後樂園】與金澤「兼六園」、水戶「偕樂園」並稱日本三大名園，為江戶時代岡山藩主池田綱政花費１４年的時間於1700年完成，採借景法之池泉迴遊式庭園，庭中有田園、茶田、梅林等，而庭園中心的澤之池可眺望備前富士以及岡山城，視野堪稱一絕，美不勝收。	【島波海道】島波海道是於西元 2006 年 4 月全線開通的公路，無需使用船隻即可從本州到四國，連接日本廣島縣尾道市和愛媛縣今治市的公路。不僅是在日本國內有名，也是享譽全球的「享受大自治市的公路，也是享譽全球的「享受大自然的自行車旅行景點」。其中多多羅大橋是位於日本瀨戶內海的斜拉橋，連接廣島縣尾道市的生口島及愛媛縣今治市的大三島之間。來島海峽大橋位於愛媛縣東北部的今治市，建於 1999 年，是世界上第一座三聯式吊橋。來島海峽大橋是西瀨戶自動車道（連結廣島縣和愛媛縣的高速公路，全長達 60 公里）中最大的一座橋。	【道後溫泉街散策】＜道後溫泉＞據說約3000年前開池，號稱日本最古老的溫泉。道後溫泉之所以有名，全拜一幢日本天皇、文學家夏目漱石住過的「道後溫泉本館」。並且是宮崎駿著名的電影創作《神隱少女》油湯原場景所在，雖然這裡看不到湯婆婆、白龍等，建於1894年的公共浴場～道後溫泉本館，其位於旅館和飯店林立的溫泉街中心，是道後的象徵。\n餐 食	住 宿\n午餐／機上精緻餐食+日式壽司餐盒￥1200	晚餐／飯店內和洋式自助餐 或 飯店內會席料理 或 外用日式鍋物御膳 或 日式風味餐	道後彩朝樂 或 道後椿館別館 或 道後LUNA PARK 或 汐之丸 或 壹湯之守 或 今治國際 或 松山東急REI 或 COMFORT 或 CANDEO\n（或同級）\n\n\n第二天	米其林綠指南二星景點～松山城(搭乘纜車、不上天守閣)	－日本奇蹟的清流～仁淀川	－日本海岸百選．桂濱公園散策～坂本龍馬像	－高知人的廚房～弘人市場\n【松山城】聳立於松山市中心地區海拔132ｍ勝山之上的松山城，是日本保留江戶時代以前所建天守閣的現存12城之一。1602年起開始築城，到完成之前花費了25年的歲月。以日式建築中典型的連立式平山城，是日本目前少數僅存的古城，此地已被日本政府指定為重要的文化財產；建於1627年，與本州的姬路城、和歌山城，並列為三大連立式平山城。於1854年重建，此時重建的天守閣就是現在的天守閣。寬大的占地內建有箭樓、門等很多建築，大天守、以及日本唯一現存的望樓型二重箭樓“野原櫓”、一之門等21棟被指定為重要文物。	【仁淀川】被譽為日本最美的河川，水流清澈且泛出神秘夢幻的鈷藍色，人們將這種藍稱為「仁淀藍」。沿著澄澈碧 藍的仁淀川健行，欣賞河川的壯麗景致。	【桂濱公園】位於龍頭岬和龍王岬間的桂濱，翠綠的松林，色彩繽紛的小石子，碧藍的太平洋，如大自然敞開大門迎客的庭院，成為高知代表的觀光名所。附近有座紀念幕府末期名人坂本龍馬、極具代表性的【坂本龍馬像】伴著壯闊的太平洋，吸引各地遊客到訪。	【弘人市場】高知旅遊必訪美食聚集地，集結約 60 間攤商，在地美食美酒諸如高知靈魂美食「鰹魚半敲燒」、夢幻「土佐褐毛牛」牛排、「高知 18 酒造」等各式日本酒，以及異國料理應有盡有，被稱為「高知的廚房」	\n餐 食	住 宿\n早餐／飯店內享用	午餐／四國高知御膳 或 日式旬御膳	晚餐／飯店內會席料理 或 豪華迎賓自助餐	高知Mercure 或 高砂 或 城西館 或 高知阪急或 土佐御苑 或 三翠園 或 三陽莊 （或同級）\n\n\n第三天	日本三大祕境~小步危‧大步危遊船	－日本三大奇橋~祖谷溪．葛藤奇橋	－一生必參拜之道海之守護神．金刀比羅宮－免稅店	－《高松AEON購物商城》~自由逛街購物\n【小步危‧大步危遊船】吉野川兩岸的斷崖近迫、深V字型的溪谷。此處有因激流形成深谷約8公里長的溪谷地帶。大步危，小步危的意思諸說紛紜，有一說是指山腹間危險地帶，也有說無論是步走都是極危險之地帶。而搭乘大步危遊覽船由大步峽出發順流而下更可進一步體驗山谷之美，沿途溪谷岩壁交錯，可見識到德島縣天然記念物~含礫變岩形成之蝙蝠岩、獅子岩等特殊奇景。	【祖谷溪】因吉野川上游支流的『祖谷溪』流經了四國的山脈而形成天然的秘境溪谷，祖谷川河水充沛河床寬大蛇行兩峰高100公尺以上的高山斷崖連綿聳立在初夏時節的新綠或是深秋時的紅葉天是添其加剛中帶柔的壯麗；同時也因為地形關係而切割出令人讚嘆不已的小步危大步危峽谷奇景。	【葛藤奇橋】祖谷葛橋かずら日本國家和德島縣的重要有形民俗文化財產，又名【蔓藤橋】橋長４５公尺、寬２公尺、高１５公尺，是由葛籐編造而成的，渡橋時搖搖擺擺，驚險無比。據傳說在戰爭中失敗的平氏族人而藏身於祖谷溪而為了逃避源氏的追剎因而搭建隨時可以斬斷追兵去路的葛製吊橋，為了安全上的考量每三年將會更換一次藤葛以供遊客前遊玩觀賞，採由北至南單向通行的渡橋方式，步行時的晃動再加上橋下清晰可見的溪流令人震撼不已。\n【金刀比羅宮】是位於日本香川縣仲多度郡琴平町的神社。舊社格為國幣中社。日本全國金刀比羅神社、琴平神社、金比羅神社之總本宮。此宮供奉海上交通之守護神，因此不少造船廠或是船東於新船下水啟用前會前來祈求航行平安，因此本宮旁的繪馬殿可見到掛滿祈求平安後留下的各種民用、軍用、政府用甚至外國船隻及船用引擎的照片。	【免稅店】您可購買精美禮品回台饋贈親友。	【高松AEON MALL】永旺購物中心高松，是一間能滿足吃喝玩樂買等需求的大型購物中心。由多家時裝店、雜貨店、餐廳、專賣店及各種綜合服務設施構成。可以欣賞現代美術和香川傳統工藝的藝術景點該美術館毗鄰商店街，地腳便利，是一座具有現代風格和沈靜氛圍的藝術景點。傲人的展示面積在四國地區首屈一指。\n餐 食	住 宿\n早餐／飯店內享用	午餐／祖谷香魚御膳 或 日式鍋物御膳	晚餐／飯店內會席料理 或 豪華迎賓自助餐	德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或 高松ROUTE INN （或同級）\n\n\n第四天	高松港～搭乘渡輪欣賞瀨戶內海明媚風光土庄港	－魔女宅急便拍攝地．小豆島橄欖樹公園~橄欖樹紀念館	－日本百景．三大溪谷~寒霞溪公園~搭乘單程纜車	－戀人聖地~天使散步道	－小豆島~土庄港~搭乘渡輪欣賞瀨戶內海明媚風光高松港\n【遊覽船土庄港－小豆島】遊覽船觀光在港口搭乘快艇遊船前往小豆島，沿途可欣賞瀨戶內海與海上島嶼的自然美景，，與藍天碧海相呼應，感受秋季特有的海上風光。土瀏海峽全長2.5公里，而寬度只有9.93公尺，是被公認為世界第一窄的海峽。	【小豆島橄欖樹公園】佔地面積3公頃，是日本最早引進橄欖樹種植的地區，栽植了來自世界各地約2000棵橄欖樹。彷彿地中海沿岸風光的小豆島橄欖公園，也是魔女宅急便電影的拍攝處。1989年，小豆島與希臘愛琴海上的米羅島結為姊妹島，興建了小豆島目前的風車地標，讓人有如置身於浪漫的地中海小鎮。	【寒霞溪公園】寒霞溪溪谷位於瀨戶內海國立公園內，是日本最美的自然奇觀之一。溪谷高處位於公園上方812公尺處，為日本政府認定為「特殊美景地區」。寒霞溪是需要愛護的自然寶藏，自1898起便有專屬的保育協會。1912年險為政府收回，好在一間大型醬油公司出面解救。此後，寒霞溪持續廣受歡迎，確保了此自然美景區的地位。尤其在秋季，當樹葉紛紛換上繽紛色彩時，更是引人入勝。飽覽溪谷壯麗景色的最佳方式，就是搭乘寒霞溪纜車從高處俯瞰。	【天使步道散策】有戀人聖地之美稱，位於銀波浦內海外灘的島嶼，每天都有 2 次的機會因為潮汐關係在海水退潮時浮現出沙洲步道連結串起各島嶼，趁著退潮時刻一定要散步穿越這「天使步道」傳說相愛的戀人一起牽手走過天使步道戀情將會長長久久，在小島上不時可看見許多祈願繪馬懸掛在樹枝上戀人們的山盟海誓處處可見，歡迎您也一同來感受一下這浪漫的魅力!!\n餐 食	住 宿\n早餐／飯店內享用	午餐／小豆島當地特色御膳	晚餐／飯店內會席料理 或 豪華迎賓自助餐	德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或高松ROUTE INN （或同級）\n\n\n第五天	高松空港/高雄小港國際機場\n早餐後前往＜高松空港＞搭乘客機飛返高雄，帶著依依不捨的心情及甜美的回憶，結束此一愉快又豐富的【四國秘境小豆島跳島】五日，祝福各位旅客旅途愉快。\n餐 食	住 宿\n早餐／飯店內享用	午餐／機上套餐	甜蜜的家\n\n\n【注意事項】\n1.本行程按航空公司之規定需「團進團出」，不可延回、不得退票及延期使用。\n2.班機時間以航空公司最後奉核時間為準。\n3.住宿飯店及行程先後秩序以說明會資料為主。如遇交通機關、道路狀況或氣候等不可抗拒因素導致行程必須臨時變動，本公司保有行程調整權，請以當地導遊安排順序為準；敬請見諒。\n\n', 'confirmed', NULL, 4, '2026-08-06 15:09:47');

-- --------------------------------------------------------

--
-- 資料表結構 `ai_parsed_day`
--

CREATE TABLE `ai_parsed_day` (
  `APDID` int(11) NOT NULL,
  `IPID` int(11) NOT NULL,
  `day_number` int(11) NOT NULL,
  `theme` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `ai_parsed_day`
--

INSERT INTO `ai_parsed_day` (`APDID`, `IPID`, `day_number`, `theme`) VALUES
(1, 2, 1, '高雄飛東京成田-採草莓體驗-會津地區溫泉'),
(2, 2, 2, '大內宿-只見線-豬苗代湖-會津若松城'),
(3, 2, 3, '藏王樹冰纜車-雪盆戲雪-銀山溫泉街'),
(4, 2, 4, '松島遊覽船-仙台城跡-新幹線移動成田'),
(5, 2, 5, '成田機場返回高雄'),
(6, 3, 1, '高松抵達～岡山後樂園～道後溫泉街散策'),
(7, 3, 2, '松山城～仁淀川～桂濱公園～弘人市場'),
(8, 3, 3, '大步危小步危遊船～祖谷溪葛藤橋～金刀比羅宮～高松AEON購物'),
(9, 3, 4, '小豆島跳島～橄欖公園～寒霞溪～天使散步道'),
(10, 3, 5, '高松返回高雄');

-- --------------------------------------------------------

--
-- 資料表結構 `ai_parsed_item`
--

CREATE TABLE `ai_parsed_item` (
  `APIID` int(11) NOT NULL,
  `APDID` int(11) NOT NULL,
  `item_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `time_slot` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `matched_pid` int(11) DEFAULT NULL,
  `sort_order` int(11) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `ai_parsed_item`
--

INSERT INTO `ai_parsed_item` (`APIID`, `APDID`, `item_type`, `name`, `time_slot`, `note`, `matched_pid`, `sort_order`) VALUES
(1, 1, 'transport', '長榮航空 BR108', 'morning', '高雄小港機場／成田國際機場 0700/1125(預定)', NULL, 0),
(2, 1, 'meal', '機上套餐', 'breakfast', '', NULL, 1),
(3, 1, 'attraction', '採草莓體驗', 'afternoon', '冬季限定!雪中採草莓吃到飽，若遇草莓園休園或塞車則改贈每人草莓一盒', NULL, 2),
(4, 1, 'meal', '日式壽司餐盒+日本茶', 'lunch', '特別安排', NULL, 3),
(5, 1, 'attraction', '會津地區', 'afternoon', '福島縣自然資源豐富，磐梯山、裏磐梯、花見山等美景，眾多溫泉鄉', NULL, 4),
(6, 1, 'meal', '迎賓和洋式自助餐或溫泉會席料理', 'dinner', '', NULL, 5),
(7, 1, 'hotel', '豬苗代湖LISTEL或裏盤梯LAKE RESORT或裏盤梯美居或裏盤梯星野或同級', 'evening', '', NULL, 6),
(8, 2, 'meal', '飯店內早餐', 'breakfast', '', NULL, 0),
(9, 2, 'attraction', '大內宿', 'morning', '日本三大茅草合掌造部落之一，保留約四十間茅草屋古樸景致', NULL, 1),
(10, 2, 'attraction', '第一只見川橋梁展望台', 'morning', '只見線最代表性拍攝景點，絕美冬景色，世界最美鐵道', NULL, 2),
(11, 2, 'meal', '手打蕎麥御膳或日式鍋物料理', 'lunch', '', NULL, 3),
(12, 2, 'attraction', '會津若松城', 'afternoon', '日本百大名城之一，又名『鶴城』，不上天守閣', NULL, 4),
(13, 2, 'attraction', '豬苗代湖', 'afternoon', '日本第四大湖，觀賞水花冰奇觀，又稱天鏡湖', NULL, 5),
(14, 2, 'meal', '日式涮涮鍋食放題+軟式飲料暢飲或飯店內用迎賓和洋式自助餐或溫泉會席料理', 'dinner', '', NULL, 6),
(15, 2, 'hotel', '天童瀧之湯或榮屋或美味求真之宿或秋保溫泉Crescent Resort或藏王美居水療渡假村仙台京阪或蒙特利或大都會或同級', 'evening', '', NULL, 7),
(16, 3, 'meal', '飯店內早餐', 'breakfast', '', NULL, 0),
(17, 3, 'attraction', '藏王纜車(藏王山麓站-地藏山頂駅)', 'morning', '登上藏王高原欣賞樹冰奇景，如遇天候不佳依說明退費規則辦理', NULL, 1),
(18, 3, 'highlight', '雪盆戲雪', 'morning', '特別安排滑雪盆、堆雪人、打雪仗', NULL, 2),
(19, 3, 'meal', '藏王風味定食或發放代金￥3000自理', 'lunch', '', NULL, 3),
(20, 3, 'attraction', '銀山溫泉街', 'afternoon', '大正浪漫風情，雪中散策及足湯體驗，因電視劇阿信聞名', NULL, 4),
(21, 3, 'meal', '日式涮涮鍋食放題+軟式飲料暢飲', 'dinner', '', NULL, 5),
(22, 3, 'hotel', '仙台蒙特利或京阪或大都會或國際或JALCITY或HILLS或同級', 'evening', '', NULL, 6),
(23, 4, 'meal', '飯店內早餐', 'breakfast', '', NULL, 0),
(24, 4, 'attraction', '松島', 'morning', '日本三景之一，搭乘遊覽船，特別加贈烤魚板+仙台名菓萩之月奶油蛋糕', NULL, 1),
(25, 4, 'attraction', '五大堂', 'morning', '千年歷史木造建築，日本重要文化財', NULL, 2),
(26, 4, 'meal', '松島名物牛舌、牡蠣風味御膳', 'lunch', '', NULL, 3),
(27, 4, 'attraction', '鹽竈神社', 'afternoon', '奧州第一宮，供奉鹽土老翁神', NULL, 4),
(28, 4, 'attraction', '仙台城跡(青葉城)', 'afternoon', '伊達政宗所築城遺跡，可一覽仙台全貌', NULL, 5),
(29, 4, 'attraction', '免稅店', 'afternoon', '參觀選購各項具特色商品', NULL, 6),
(30, 4, 'transport', '新幹線子彈列車', 'afternoon', '體驗世界第一日本新幹線，仙台至東京', NULL, 7),
(31, 4, 'meal', '日式燒肉吃到飽或日式風味御膳或飯店內用和洋式自助餐', 'dinner', '', NULL, 8),
(32, 4, 'hotel', '幕張新大谷或幕張APA或成田馬洛德或ANA CROWN PLAZA或MYSTAYS或同級', 'evening', '', NULL, 9),
(33, 5, 'meal', '飯店內早餐', 'breakfast', '', NULL, 0),
(34, 5, 'attraction', '成田國際機場免稅店', 'morning', '日本最大機場免稅店，可購買糖果及世界高級品牌商品', NULL, 1),
(35, 5, 'transport', '長榮航空 BR107', 'noon', '成田國際機場／高雄小港機場 1225/1545(預定)', NULL, 2),
(36, 5, 'meal', '機上套餐', 'lunch', '', NULL, 3),
(37, 5, 'highlight', '溫暖的家', NULL, '團進團出，不可延回、不得退票及延期使用', NULL, 4),
(38, 6, 'transport', '中華航空CI7760 高雄小港/高松', 'morning', '0635/1030(預定)，10/26起0655/1035(預定)', NULL, 0),
(39, 6, 'attraction', '岡山後樂園', 'morning', '日本三大名園之一，池泉迴遊式庭園，可眺望備前富士及岡山城', NULL, 1),
(40, 6, 'attraction', '島波海道(多多羅大橋、來島大橋)', 'afternoon', '行經瀨戶內海，連接本州與四國的跨海公路，含多多羅大橋、來島海峽大橋', NULL, 2),
(41, 6, 'attraction', '道後溫泉街散策', 'afternoon', '神隱少女湯屋原場景，日本最古老溫泉，含少爺音樂鐘、放生園足湯', NULL, 3),
(42, 6, 'meal', '機上精緻餐食+日式壽司餐盒', 'lunch', '￥1200', NULL, 4),
(43, 6, 'meal', '飯店內和洋式自助餐或會席料理或日式鍋物御膳或日式風味餐', 'dinner', '', NULL, 5),
(44, 6, 'hotel', '道後彩朝樂 或 道後椿館別館 或 道後LUNA PARK 或 汐之丸 或 壹湯之守 或 今治國際 或 松山東急REI 或 COMFORT 或 CANDEO', NULL, '或同級', NULL, 6),
(45, 7, 'attraction', '松山城', 'morning', '米其林綠指南二星景點，搭乘纜車、不上天守閣，日本現存12城之一', NULL, 0),
(46, 7, 'attraction', '仁淀川', 'morning', '日本奇蹟的清流，水色呈現神秘鈷藍色，仁淀藍', NULL, 1),
(47, 7, 'attraction', '桂濱公園', 'afternoon', '日本海岸百選，含坂本龍馬像', NULL, 2),
(48, 7, 'attraction', '弘人市場', 'afternoon', '高知人的廚房，約60間攤商，鰹魚半敲燒、土佐褐毛牛等美食', NULL, 3),
(49, 7, 'meal', '飯店內早餐', 'breakfast', '', NULL, 4),
(50, 7, 'meal', '四國高知御膳或日式旬御膳', 'lunch', '', NULL, 5),
(51, 7, 'meal', '飯店內會席料理或豪華迎賓自助餐', 'dinner', '', NULL, 6),
(52, 7, 'hotel', '高知Mercure 或 高砂 或 城西館 或 高知阪急 或 土佐御苑 或 三翠園 或 三陽莊', NULL, '或同級', NULL, 7),
(53, 8, 'attraction', '小步危‧大步危遊船', 'morning', '日本三大祕境，吉野川溪谷，可見蝙蝠岩、獅子岩等奇景', NULL, 0),
(54, 8, 'attraction', '祖谷溪', 'morning', '天然秘境溪谷，斷崖連綿，四季景色壯麗', NULL, 1),
(55, 8, 'attraction', '葛藤奇橋(蔓藤橋)', 'morning', '日本三大奇橋，橋長45公尺，寬2公尺，高15公尺，由葛籐編造而成', NULL, 2),
(56, 8, 'attraction', '金刀比羅宮', 'afternoon', '海上交通守護神，繪馬殿掛滿祈求平安的照片', NULL, 3),
(57, 8, 'attraction', '免稅店', 'afternoon', '購買精美禮品回台饋贈親友', NULL, 4),
(58, 8, 'attraction', '高松AEON購物商城', 'afternoon', '自由逛街購物，大型購物中心，含美術館', NULL, 5),
(59, 8, 'meal', '飯店內早餐', 'breakfast', '', NULL, 6),
(60, 8, 'meal', '祖谷香魚御膳或日式鍋物御膳', 'lunch', '', NULL, 7),
(61, 8, 'meal', '飯店內會席料理或豪華迎賓自助餐', 'dinner', '', NULL, 8),
(62, 8, 'hotel', '德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或 高松ROUTE INN', NULL, '或同級', NULL, 9),
(63, 9, 'transport', '渡輪高松港－土庄港', 'morning', '搭乘渡輪欣賞瀨戶內海明媚風光，往小豆島', NULL, 0),
(64, 9, 'attraction', '小豆島橄欖樹公園', 'morning', '魔女宅急便拍攝地，約2000棵橄欖樹，含橄欖樹紀念館，與希臘米羅島結為姊妹島', NULL, 1),
(65, 9, 'attraction', '寒霞溪公園', 'afternoon', '日本百景、三大溪谷之一，搭乘單程纜車，秋季紅葉美景', NULL, 2),
(66, 9, 'attraction', '天使散步道', 'afternoon', '戀人聖地，退潮時浮現沙洲步道連結島嶼', NULL, 3),
(67, 9, 'transport', '渡輪土庄港－高松港', 'afternoon', '搭乘渡輪返回高松，欣賞瀨戶內海風光', NULL, 4),
(68, 9, 'meal', '飯店內早餐', 'breakfast', '', NULL, 5),
(69, 9, 'meal', '小豆島當地特色御膳', 'lunch', '', NULL, 6),
(70, 9, 'meal', '飯店內會席料理或豪華迎賓自助餐', 'dinner', '', NULL, 7),
(71, 9, 'hotel', '德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或 高松ROUTE INN', NULL, '或同級', NULL, 8),
(72, 10, 'transport', '中華航空CI7761 高松/高雄小港', 'morning', '1130/1335(預定)，10/26起1135/1405(預定)', NULL, 0),
(73, 10, 'meal', '飯店內早餐', 'breakfast', '', NULL, 1),
(74, 10, 'meal', '機上套餐', 'lunch', '', NULL, 2),
(75, 10, 'highlight', '行程結束', NULL, '帶著依依不捨的心情及甜美的回憶，結束此一愉快又豐富的四國秘境小豆島跳島五日行程', NULL, 3);

-- --------------------------------------------------------

--
-- 資料表結構 `component`
--

CREATE TABLE `component` (
  `CPID` int(11) NOT NULL,
  `AID` int(11) NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_price` decimal(10,2) DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- 資料表結構 `export_history`
--

CREATE TABLE `export_history` (
  `EHID` int(11) NOT NULL,
  `ITID` int(11) NOT NULL,
  `format` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `generated_by` int(11) DEFAULT NULL,
  `generated_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- 資料表結構 `itinerary`
--

CREATE TABLE `itinerary` (
  `ITID` int(11) NOT NULL,
  `AID` int(11) NOT NULL,
  `created_by` int(11) NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `days_count` int(11) NOT NULL DEFAULT '1',
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `group_size` int(11) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'draft',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `itinerary`
--

INSERT INTO `itinerary` (`ITID`, `AID`, `created_by`, `title`, `country`, `days_count`, `start_date`, `end_date`, `group_size`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, 1, '北海道經典六日游', '日本北海道', 5, '2026-08-06', '2026-08-10', NULL, 'draft', '2026-08-06 09:29:02', '2026-08-06 09:29:02'),
(2, 1, 1, '北海道3日遊', '日本北海道', 3, '2026-09-07', '2026-09-09', NULL, 'draft', '2026-08-06 09:41:24', '2026-08-06 09:41:24'),
(3, 1, 1, '【冰雪秘境】日本東北藏王樹冰絕景、銀山溫泉5日大內宿、會津若松城、新幹線子彈列車', '日本東北', 5, '2026-08-06', '2026-08-10', NULL, 'draft', '2026-08-06 15:02:46', '2026-08-06 15:02:46'),
(4, 1, 1, '【四國秘境全覽】小豆島跳島溫泉美食五日', '日本四國', 5, '2026-08-06', '2026-08-10', NULL, 'draft', '2026-08-06 15:27:53', '2026-08-06 15:27:53');

-- --------------------------------------------------------

--
-- 資料表結構 `itinerary_component`
--

CREATE TABLE `itinerary_component` (
  `ICID` int(11) NOT NULL,
  `ITID` int(11) NOT NULL,
  `CPID` int(11) NOT NULL,
  `day_number` int(11) DEFAULT NULL,
  `quantity` int(11) DEFAULT '1',
  `price_override` decimal(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- 資料表結構 `itinerary_day`
--

CREATE TABLE `itinerary_day` (
  `IDID` int(11) NOT NULL,
  `ITID` int(11) NOT NULL,
  `day_number` int(11) NOT NULL,
  `day_date` date DEFAULT NULL,
  `theme` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `itinerary_day`
--

INSERT INTO `itinerary_day` (`IDID`, `ITID`, `day_number`, `day_date`, `theme`) VALUES
(1, 1, 1, '2026-08-06', NULL),
(2, 1, 2, '2026-08-07', NULL),
(3, 1, 3, '2026-08-08', NULL),
(4, 1, 4, '2026-08-09', NULL),
(5, 1, 5, '2026-08-10', NULL),
(6, 2, 1, '2026-09-07', NULL),
(7, 2, 2, '2026-09-08', NULL),
(8, 2, 3, '2026-09-09', NULL),
(9, 3, 1, '2026-08-06', NULL),
(10, 3, 2, '2026-08-07', NULL),
(11, 3, 3, '2026-08-08', NULL),
(12, 3, 4, '2026-08-09', NULL),
(13, 3, 5, '2026-08-10', NULL),
(14, 4, 1, '2026-08-06', NULL),
(15, 4, 2, '2026-08-07', NULL),
(16, 4, 3, '2026-08-08', NULL),
(17, 4, 4, '2026-08-09', NULL),
(18, 4, 5, '2026-08-10', NULL);

-- --------------------------------------------------------

--
-- 資料表結構 `itinerary_item`
--

CREATE TABLE `itinerary_item` (
  `IIID` int(11) NOT NULL,
  `IDID` int(11) NOT NULL,
  `PID` int(11) DEFAULT NULL,
  `item_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `custom_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `start_time` time DEFAULT NULL,
  `end_time` time DEFAULT NULL,
  `stay_duration_min` int(11) DEFAULT NULL,
  `note` text COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `itinerary_item`
--

INSERT INTO `itinerary_item` (`IIID`, `IDID`, `PID`, `item_type`, `custom_name`, `sort_order`, `start_time`, `end_time`, `stay_duration_min`, `note`) VALUES
(3, 1, 1, 'attraction', '札幌', 1, NULL, NULL, NULL, NULL),
(4, 2, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(5, 3, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(6, 4, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(7, 5, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(8, 1, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(9, 1, 1, 'attraction', '札幌', 2, NULL, NULL, NULL, NULL),
(10, 6, 2, 'attraction', '小樽', 1, NULL, NULL, NULL, NULL),
(11, 6, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(12, 7, 2, 'attraction', '小樽', 2, NULL, NULL, NULL, NULL),
(13, 7, 1, 'attraction', '札幌', 1, NULL, NULL, NULL, NULL),
(14, 7, 2, 'attraction', '小樽', 0, NULL, NULL, NULL, NULL),
(15, 8, 1, 'attraction', '札幌', 1, NULL, NULL, NULL, NULL),
(16, 8, 1, 'attraction', '札幌', 0, NULL, NULL, NULL, NULL),
(17, 9, NULL, 'transport', '長榮航空 BR108', 0, NULL, NULL, NULL, NULL),
(18, 9, NULL, 'meal', '機上套餐', 1, NULL, NULL, NULL, NULL),
(19, 9, NULL, 'attraction', '採草莓體驗', 2, NULL, NULL, NULL, NULL),
(20, 9, NULL, 'meal', '日式壽司餐盒+日本茶', 3, NULL, NULL, NULL, NULL),
(21, 9, NULL, 'attraction', '會津地區', 4, NULL, NULL, NULL, NULL),
(22, 9, NULL, 'meal', '迎賓和洋式自助餐或溫泉會席料理', 5, NULL, NULL, NULL, NULL),
(23, 9, NULL, 'hotel', '豬苗代湖LISTEL或裏盤梯LAKE RESORT或裏盤梯美居或裏盤梯星野或同級', 6, NULL, NULL, NULL, NULL),
(24, 10, NULL, 'meal', '飯店內早餐', 0, NULL, NULL, NULL, NULL),
(25, 10, NULL, 'attraction', '大內宿', 1, NULL, NULL, NULL, NULL),
(26, 10, NULL, 'attraction', '第一只見川橋梁展望台', 2, NULL, NULL, NULL, NULL),
(27, 10, NULL, 'meal', '手打蕎麥御膳或日式鍋物料理', 3, NULL, NULL, NULL, NULL),
(28, 10, NULL, 'attraction', '會津若松城', 4, NULL, NULL, NULL, NULL),
(29, 10, NULL, 'attraction', '豬苗代湖', 5, NULL, NULL, NULL, NULL),
(30, 10, NULL, 'meal', '日式涮涮鍋食放題+軟式飲料暢飲或飯店內用迎賓和洋式自助餐或溫泉會席料理', 6, NULL, NULL, NULL, NULL),
(31, 10, NULL, 'hotel', '天童瀧之湯或榮屋或美味求真之宿或秋保溫泉Crescent Resort或藏王美居水療渡假村仙台京阪或蒙特利或大都會或同級', 7, NULL, NULL, NULL, NULL),
(32, 11, NULL, 'meal', '飯店內早餐', 0, NULL, NULL, NULL, NULL),
(33, 11, NULL, 'attraction', '藏王纜車(藏王山麓站-地藏山頂駅)', 1, NULL, NULL, NULL, NULL),
(34, 11, NULL, 'highlight', '雪盆戲雪', 2, NULL, NULL, NULL, NULL),
(35, 11, NULL, 'meal', '藏王風味定食或發放代金￥3000自理', 3, NULL, NULL, NULL, NULL),
(36, 11, NULL, 'attraction', '銀山溫泉街', 4, NULL, NULL, NULL, NULL),
(37, 11, NULL, 'meal', '日式涮涮鍋食放題+軟式飲料暢飲', 5, NULL, NULL, NULL, NULL),
(38, 11, NULL, 'hotel', '仙台蒙特利或京阪或大都會或國際或JALCITY或HILLS或同級', 6, NULL, NULL, NULL, NULL),
(39, 12, NULL, 'meal', '飯店內早餐', 0, NULL, NULL, NULL, NULL),
(40, 12, NULL, 'attraction', '松島', 1, NULL, NULL, NULL, NULL),
(41, 12, NULL, 'attraction', '五大堂', 2, NULL, NULL, NULL, NULL),
(42, 12, NULL, 'meal', '松島名物牛舌、牡蠣風味御膳', 3, NULL, NULL, NULL, NULL),
(43, 12, NULL, 'attraction', '鹽竈神社', 4, NULL, NULL, NULL, NULL),
(44, 12, NULL, 'attraction', '仙台城跡(青葉城)', 5, NULL, NULL, NULL, NULL),
(45, 12, NULL, 'attraction', '免稅店', 6, NULL, NULL, NULL, NULL),
(46, 12, NULL, 'transport', '新幹線子彈列車', 7, NULL, NULL, NULL, NULL),
(47, 12, NULL, 'meal', '日式燒肉吃到飽或日式風味御膳或飯店內用和洋式自助餐', 8, NULL, NULL, NULL, NULL),
(48, 12, NULL, 'hotel', '幕張新大谷或幕張APA或成田馬洛德或ANA CROWN PLAZA或MYSTAYS或同級', 9, NULL, NULL, NULL, NULL),
(49, 13, NULL, 'meal', '飯店內早餐', 0, NULL, NULL, NULL, NULL),
(50, 13, NULL, 'attraction', '成田國際機場免稅店', 1, NULL, NULL, NULL, NULL),
(51, 13, NULL, 'transport', '長榮航空 BR107', 2, NULL, NULL, NULL, NULL),
(52, 13, NULL, 'meal', '機上套餐', 3, NULL, NULL, NULL, NULL),
(53, 13, NULL, 'highlight', '溫暖的家', 4, NULL, NULL, NULL, NULL),
(54, 14, NULL, 'transport', '中華航空CI7760 高雄小港/高松', 0, NULL, NULL, NULL, NULL),
(55, 14, NULL, 'attraction', '岡山後樂園', 1, NULL, NULL, NULL, NULL),
(56, 14, NULL, 'attraction', '島波海道(多多羅大橋、來島大橋)', 2, NULL, NULL, NULL, NULL),
(57, 14, NULL, 'attraction', '道後溫泉街散策', 3, NULL, NULL, NULL, NULL),
(58, 14, NULL, 'meal', '機上精緻餐食+日式壽司餐盒', 4, NULL, NULL, NULL, NULL),
(59, 14, NULL, 'meal', '飯店內和洋式自助餐或會席料理或日式鍋物御膳或日式風味餐', 5, NULL, NULL, NULL, NULL),
(60, 14, NULL, 'hotel', '道後彩朝樂 或 道後椿館別館 或 道後LUNA PARK 或 汐之丸 或 壹湯之守 或 今治國際 或 松山東急REI 或 COMFORT 或 CANDEO', 6, NULL, NULL, NULL, NULL),
(61, 15, NULL, 'attraction', '松山城', 0, NULL, NULL, NULL, NULL),
(62, 15, NULL, 'attraction', '仁淀川', 1, NULL, NULL, NULL, NULL),
(63, 15, NULL, 'attraction', '桂濱公園', 2, NULL, NULL, NULL, NULL),
(64, 15, NULL, 'attraction', '弘人市場', 3, NULL, NULL, NULL, NULL),
(65, 15, NULL, 'meal', '飯店內早餐', 4, NULL, NULL, NULL, NULL),
(66, 15, NULL, 'meal', '四國高知御膳或日式旬御膳', 5, NULL, NULL, NULL, NULL),
(67, 15, NULL, 'meal', '飯店內會席料理或豪華迎賓自助餐', 6, NULL, NULL, NULL, NULL),
(68, 15, NULL, 'hotel', '高知Mercure 或 高砂 或 城西館 或 高知阪急 或 土佐御苑 或 三翠園 或 三陽莊', 7, NULL, NULL, NULL, NULL),
(69, 16, NULL, 'attraction', '小步危‧大步危遊船', 0, NULL, NULL, NULL, NULL),
(70, 16, NULL, 'attraction', '祖谷溪', 1, NULL, NULL, NULL, NULL),
(71, 16, NULL, 'attraction', '葛藤奇橋(蔓藤橋)', 2, NULL, NULL, NULL, NULL),
(72, 16, NULL, 'attraction', '金刀比羅宮', 3, NULL, NULL, NULL, NULL),
(73, 16, NULL, 'attraction', '免稅店', 4, NULL, NULL, NULL, NULL),
(74, 16, NULL, 'attraction', '高松AEON購物商城', 5, NULL, NULL, NULL, NULL),
(75, 16, NULL, 'meal', '飯店內早餐', 6, NULL, NULL, NULL, NULL),
(76, 16, NULL, 'meal', '祖谷香魚御膳或日式鍋物御膳', 7, NULL, NULL, NULL, NULL),
(77, 16, NULL, 'meal', '飯店內會席料理或豪華迎賓自助餐', 8, NULL, NULL, NULL, NULL),
(78, 16, NULL, 'hotel', '德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或 高松ROUTE INN', 9, NULL, NULL, NULL, NULL),
(79, 17, NULL, 'transport', '渡輪高松港－土庄港', 0, NULL, NULL, NULL, NULL),
(80, 17, NULL, 'attraction', '小豆島橄欖樹公園', 1, NULL, NULL, NULL, NULL),
(81, 17, NULL, 'attraction', '寒霞溪公園', 2, NULL, NULL, NULL, NULL),
(82, 17, NULL, 'attraction', '天使散步道', 3, NULL, NULL, NULL, NULL),
(83, 17, NULL, 'transport', '渡輪土庄港－高松港', 4, NULL, NULL, NULL, NULL),
(84, 17, NULL, 'meal', '飯店內早餐', 5, NULL, NULL, NULL, NULL),
(85, 17, NULL, 'meal', '小豆島當地特色御膳', 6, NULL, NULL, NULL, NULL),
(86, 17, NULL, 'meal', '飯店內會席料理或豪華迎賓自助餐', 7, NULL, NULL, NULL, NULL),
(87, 17, NULL, 'hotel', '德島GRANDVRIO 或 丸龜大倉 或 雷歐瑪渡假村 或 新樺川觀光 或 高松東急REI 或 高松JR CLEMENT 或 ROYAL PARK 或 高松ROUTE INN', 8, NULL, NULL, NULL, NULL),
(88, 18, NULL, 'transport', '中華航空CI7761 高松/高雄小港', 0, NULL, NULL, NULL, NULL),
(89, 18, NULL, 'meal', '飯店內早餐', 1, NULL, NULL, NULL, NULL),
(90, 18, NULL, 'meal', '機上套餐', 2, NULL, NULL, NULL, NULL),
(91, 18, NULL, 'highlight', '行程結束', 3, NULL, NULL, NULL, NULL);

-- --------------------------------------------------------

--
-- 資料表結構 `poi`
--

CREATE TABLE `poi` (
  `PID` int(11) NOT NULL,
  `AID` int(11) DEFAULT NULL,
  `category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `suggested_stay_min` int(11) DEFAULT '60',
  `open_hours` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `star_rating` decimal(2,1) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `poi`
--

INSERT INTO `poi` (`PID`, `AID`, `category`, `name`, `country`, `city`, `address`, `latitude`, `longitude`, `suggested_stay_min`, `open_hours`, `description`, `star_rating`, `created_at`) VALUES
(1, 1, 'attraction', '札幌', '', '', '', NULL, NULL, 60, NULL, '', NULL, '2026-08-06 09:30:13'),
(2, 1, 'attraction', '小樽', '', '', '', NULL, NULL, 50, NULL, '', NULL, '2026-08-06 09:41:37');

-- --------------------------------------------------------

--
-- 資料表結構 `poi_image`
--

CREATE TABLE `poi_image` (
  `IID` int(11) NOT NULL,
  `PID` int(11) NOT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int(11) DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- 資料表結構 `route_segment`
--

CREATE TABLE `route_segment` (
  `RSID` int(11) NOT NULL,
  `IDID` int(11) NOT NULL,
  `from_item_id` int(11) NOT NULL,
  `to_item_id` int(11) NOT NULL,
  `distance_km` decimal(6,2) DEFAULT NULL,
  `duration_min` int(11) DEFAULT NULL,
  `transport_mode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'driving',
  `is_backtrack` tinyint(1) DEFAULT '0',
  `calculated_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- 資料表結構 `staff_user`
--

CREATE TABLE `staff_user` (
  `UID` int(11) NOT NULL,
  `AID` int(11) NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `pw` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'OP',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- 資料表的匯出資料 `staff_user`
--

INSERT INTO `staff_user` (`UID`, `AID`, `name`, `phone`, `account`, `pw`, `role`, `created_at`) VALUES
(1, 1, '黃子恩', '0938678687', 'test', '$2a$10$XQVPY0Ptg/2YKe9Zh91NR.CUYmPS3nUzqXZun6lRIbYByBB4h/Y56', 'ADMIN', '2026-08-06 09:28:20');

--
-- 已匯出資料表的索引
--

--
-- 資料表索引 `agency`
--
ALTER TABLE `agency`
  ADD PRIMARY KEY (`AID`);

--
-- 資料表索引 `ai_import`
--
ALTER TABLE `ai_import`
  ADD PRIMARY KEY (`IPID`),
  ADD KEY `AID` (`AID`),
  ADD KEY `created_by` (`created_by`),
  ADD KEY `result_itinerary_id` (`result_itinerary_id`);

--
-- 資料表索引 `ai_parsed_day`
--
ALTER TABLE `ai_parsed_day`
  ADD PRIMARY KEY (`APDID`),
  ADD KEY `IPID` (`IPID`);

--
-- 資料表索引 `ai_parsed_item`
--
ALTER TABLE `ai_parsed_item`
  ADD PRIMARY KEY (`APIID`),
  ADD KEY `APDID` (`APDID`),
  ADD KEY `matched_pid` (`matched_pid`);

--
-- 資料表索引 `component`
--
ALTER TABLE `component`
  ADD PRIMARY KEY (`CPID`),
  ADD KEY `AID` (`AID`);

--
-- 資料表索引 `export_history`
--
ALTER TABLE `export_history`
  ADD PRIMARY KEY (`EHID`),
  ADD KEY `ITID` (`ITID`),
  ADD KEY `generated_by` (`generated_by`);

--
-- 資料表索引 `itinerary`
--
ALTER TABLE `itinerary`
  ADD PRIMARY KEY (`ITID`),
  ADD KEY `AID` (`AID`),
  ADD KEY `created_by` (`created_by`);

--
-- 資料表索引 `itinerary_component`
--
ALTER TABLE `itinerary_component`
  ADD PRIMARY KEY (`ICID`),
  ADD KEY `ITID` (`ITID`),
  ADD KEY `CPID` (`CPID`);

--
-- 資料表索引 `itinerary_day`
--
ALTER TABLE `itinerary_day`
  ADD PRIMARY KEY (`IDID`),
  ADD UNIQUE KEY `uk_day` (`ITID`,`day_number`);

--
-- 資料表索引 `itinerary_item`
--
ALTER TABLE `itinerary_item`
  ADD PRIMARY KEY (`IIID`),
  ADD KEY `IDID` (`IDID`),
  ADD KEY `PID` (`PID`);

--
-- 資料表索引 `poi`
--
ALTER TABLE `poi`
  ADD PRIMARY KEY (`PID`),
  ADD KEY `AID` (`AID`);

--
-- 資料表索引 `poi_image`
--
ALTER TABLE `poi_image`
  ADD PRIMARY KEY (`IID`),
  ADD KEY `PID` (`PID`);

--
-- 資料表索引 `route_segment`
--
ALTER TABLE `route_segment`
  ADD PRIMARY KEY (`RSID`),
  ADD KEY `IDID` (`IDID`),
  ADD KEY `from_item_id` (`from_item_id`),
  ADD KEY `to_item_id` (`to_item_id`);

--
-- 資料表索引 `staff_user`
--
ALTER TABLE `staff_user`
  ADD PRIMARY KEY (`UID`),
  ADD UNIQUE KEY `account` (`account`),
  ADD KEY `AID` (`AID`);

--
-- 在匯出的資料表使用 AUTO_INCREMENT
--

--
-- 使用資料表 AUTO_INCREMENT `agency`
--
ALTER TABLE `agency`
  MODIFY `AID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- 使用資料表 AUTO_INCREMENT `ai_import`
--
ALTER TABLE `ai_import`
  MODIFY `IPID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- 使用資料表 AUTO_INCREMENT `ai_parsed_day`
--
ALTER TABLE `ai_parsed_day`
  MODIFY `APDID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

--
-- 使用資料表 AUTO_INCREMENT `ai_parsed_item`
--
ALTER TABLE `ai_parsed_item`
  MODIFY `APIID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=76;

--
-- 使用資料表 AUTO_INCREMENT `component`
--
ALTER TABLE `component`
  MODIFY `CPID` int(11) NOT NULL AUTO_INCREMENT;

--
-- 使用資料表 AUTO_INCREMENT `export_history`
--
ALTER TABLE `export_history`
  MODIFY `EHID` int(11) NOT NULL AUTO_INCREMENT;

--
-- 使用資料表 AUTO_INCREMENT `itinerary`
--
ALTER TABLE `itinerary`
  MODIFY `ITID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- 使用資料表 AUTO_INCREMENT `itinerary_component`
--
ALTER TABLE `itinerary_component`
  MODIFY `ICID` int(11) NOT NULL AUTO_INCREMENT;

--
-- 使用資料表 AUTO_INCREMENT `itinerary_day`
--
ALTER TABLE `itinerary_day`
  MODIFY `IDID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

--
-- 使用資料表 AUTO_INCREMENT `itinerary_item`
--
ALTER TABLE `itinerary_item`
  MODIFY `IIID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=92;

--
-- 使用資料表 AUTO_INCREMENT `poi`
--
ALTER TABLE `poi`
  MODIFY `PID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- 使用資料表 AUTO_INCREMENT `poi_image`
--
ALTER TABLE `poi_image`
  MODIFY `IID` int(11) NOT NULL AUTO_INCREMENT;

--
-- 使用資料表 AUTO_INCREMENT `route_segment`
--
ALTER TABLE `route_segment`
  MODIFY `RSID` int(11) NOT NULL AUTO_INCREMENT;

--
-- 使用資料表 AUTO_INCREMENT `staff_user`
--
ALTER TABLE `staff_user`
  MODIFY `UID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- 已匯出資料表的限制(Constraint)
--

--
-- 資料表的 Constraints `ai_import`
--
ALTER TABLE `ai_import`
  ADD CONSTRAINT `ai_import_ibfk_1` FOREIGN KEY (`AID`) REFERENCES `agency` (`AID`),
  ADD CONSTRAINT `ai_import_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `staff_user` (`UID`),
  ADD CONSTRAINT `ai_import_ibfk_3` FOREIGN KEY (`result_itinerary_id`) REFERENCES `itinerary` (`ITID`);

--
-- 資料表的 Constraints `ai_parsed_day`
--
ALTER TABLE `ai_parsed_day`
  ADD CONSTRAINT `ai_parsed_day_ibfk_1` FOREIGN KEY (`IPID`) REFERENCES `ai_import` (`IPID`) ON DELETE CASCADE;

--
-- 資料表的 Constraints `ai_parsed_item`
--
ALTER TABLE `ai_parsed_item`
  ADD CONSTRAINT `ai_parsed_item_ibfk_1` FOREIGN KEY (`APDID`) REFERENCES `ai_parsed_day` (`APDID`) ON DELETE CASCADE,
  ADD CONSTRAINT `ai_parsed_item_ibfk_2` FOREIGN KEY (`matched_pid`) REFERENCES `poi` (`PID`);

--
-- 資料表的 Constraints `component`
--
ALTER TABLE `component`
  ADD CONSTRAINT `component_ibfk_1` FOREIGN KEY (`AID`) REFERENCES `agency` (`AID`);

--
-- 資料表的 Constraints `export_history`
--
ALTER TABLE `export_history`
  ADD CONSTRAINT `export_history_ibfk_1` FOREIGN KEY (`ITID`) REFERENCES `itinerary` (`ITID`) ON DELETE CASCADE,
  ADD CONSTRAINT `export_history_ibfk_2` FOREIGN KEY (`generated_by`) REFERENCES `staff_user` (`UID`);

--
-- 資料表的 Constraints `itinerary`
--
ALTER TABLE `itinerary`
  ADD CONSTRAINT `itinerary_ibfk_1` FOREIGN KEY (`AID`) REFERENCES `agency` (`AID`),
  ADD CONSTRAINT `itinerary_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `staff_user` (`UID`);

--
-- 資料表的 Constraints `itinerary_component`
--
ALTER TABLE `itinerary_component`
  ADD CONSTRAINT `itinerary_component_ibfk_1` FOREIGN KEY (`ITID`) REFERENCES `itinerary` (`ITID`) ON DELETE CASCADE,
  ADD CONSTRAINT `itinerary_component_ibfk_2` FOREIGN KEY (`CPID`) REFERENCES `component` (`CPID`);

--
-- 資料表的 Constraints `itinerary_day`
--
ALTER TABLE `itinerary_day`
  ADD CONSTRAINT `itinerary_day_ibfk_1` FOREIGN KEY (`ITID`) REFERENCES `itinerary` (`ITID`) ON DELETE CASCADE;

--
-- 資料表的 Constraints `itinerary_item`
--
ALTER TABLE `itinerary_item`
  ADD CONSTRAINT `itinerary_item_ibfk_1` FOREIGN KEY (`IDID`) REFERENCES `itinerary_day` (`IDID`) ON DELETE CASCADE,
  ADD CONSTRAINT `itinerary_item_ibfk_2` FOREIGN KEY (`PID`) REFERENCES `poi` (`PID`);

--
-- 資料表的 Constraints `poi`
--
ALTER TABLE `poi`
  ADD CONSTRAINT `poi_ibfk_1` FOREIGN KEY (`AID`) REFERENCES `agency` (`AID`);

--
-- 資料表的 Constraints `poi_image`
--
ALTER TABLE `poi_image`
  ADD CONSTRAINT `poi_image_ibfk_1` FOREIGN KEY (`PID`) REFERENCES `poi` (`PID`) ON DELETE CASCADE;

--
-- 資料表的 Constraints `route_segment`
--
ALTER TABLE `route_segment`
  ADD CONSTRAINT `route_segment_ibfk_1` FOREIGN KEY (`IDID`) REFERENCES `itinerary_day` (`IDID`) ON DELETE CASCADE,
  ADD CONSTRAINT `route_segment_ibfk_2` FOREIGN KEY (`from_item_id`) REFERENCES `itinerary_item` (`IIID`),
  ADD CONSTRAINT `route_segment_ibfk_3` FOREIGN KEY (`to_item_id`) REFERENCES `itinerary_item` (`IIID`);

--
-- 資料表的 Constraints `staff_user`
--
ALTER TABLE `staff_user`
  ADD CONSTRAINT `staff_user_ibfk_1` FOREIGN KEY (`AID`) REFERENCES `agency` (`AID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
