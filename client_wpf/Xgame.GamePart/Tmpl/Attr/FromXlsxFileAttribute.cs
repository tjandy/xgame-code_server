﻿using System;

namespace Xgame.GamePart.Tmpl.Attr
{
    /// <summary>
    /// 从指定的 Excel 文件中读取数据
    /// </summary>
    [AttributeUsage(AttributeTargets.Class, AllowMultiple = false, Inherited = true)]
    public sealed class FromXlsxFileAttribute : Attribute
    {
        #region 类构造器
        /// <summary>
        /// 类参数构造器
        /// </summary>
        /// <param name="fileName"></param>
        /// <param name="sheetName"></param>
        /// <param name="startFromRowIndex"></param>
        public FromXlsxFileAttribute(string fileName, string sheetName, int startFromRowIndex = 1)
        {
            this.FileName = fileName;
            this.SheetName = sheetName;
        }
        #endregion

        /// <summary>
        /// 获取或设置 Excel 文件名称
        /// </summary>
        public string FileName
        {
            private set;
            get;
        }

        /// <summary>
        /// 获取或设置页签名称
        /// </summary>
        public string SheetName
        {
            get;
            set;
        }

        /// <summary>
        /// 获取或设置起始行索引
        /// </summary>
        public int StartFromRowIndex
        {
            get;
            set;
        }
    }
}
