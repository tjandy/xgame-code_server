<?php
// @import
require_once(__DIR__ . "/../AbstractWorkNode.php");
require_once(__DIR__ . "/../SQLHelper.php");

/** 坐骑 */
class S100_CombineMountAdvance extends AbstractWorkNode {
    // @Override
    function doAction($myParam) {
        // 执行 SQL 语句
        CombineHelper::combine(
        	"t_mount_advance", null, $myParam->_sheepPDO, $myParam->_wolfPDO
        );
    }
}