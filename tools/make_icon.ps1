# 生成应用图标（渐变背景 + 白色循环箭头）
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot  # cycle-cal 根目录（tools/ 的上级）
$res = Join-Path $root "app\res"
New-Item -ItemType Directory -Force -Path (Join-Path $res "drawable-nodpi") | Out-Null

function New-RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Draw-Icon([int]$size, [string]$path, [float]$contentScale) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $c1 = [System.Drawing.Color]::FromArgb(255, 106, 90, 224)   # #6A5AE0
    $c2 = [System.Drawing.Color]::FromArgb(255, 76, 95, 232)    # #4C5FE8

    if ($contentScale -ge 1.0) {
        # 完整图标：圆角渐变背景
        $bgPath = New-RoundRectPath 0 0 $size $size ($size * 0.22)
        $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
        $gb = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45)
        $g.FillPath($gb, $bgPath)
        $gb.Dispose()
        $bgPath.Dispose()
    }

    # 循环箭头（两段弧 + 箭头）
    $cx = $size / 2.0
    $cy = $size / 2.0
    $R = $size * 0.27 * $contentScale
    $penW = $size * 0.085 * $contentScale
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, $penW)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    $d = 2 * $R
    $arcRect = New-Object System.Drawing.RectangleF(($cx - $R), ($cy - $R), $d, $d)

    # 弧1：-100° 起，扫 260°（顺时针）
    $g.DrawArc($pen, $arcRect, -100, 260)
    # 弧2：80° 起，扫 260°
    $g.DrawArc($pen, $arcRect, 80, 260)

    function Add-Arrow([System.Drawing.Graphics]$gg, [float]$acx, [float]$acy, [float]$ar, [float]$deg, [float]$aw, [float]$al) {
        $rad = $deg * [Math]::PI / 180.0
        # 屏幕坐标顺时针（y 向下），角 deg 处的弧上点
        $px = $acx + $ar * [Math]::Cos($rad)
        $py = $acy + $ar * [Math]::Sin($rad)
        # 切线方向（顺时针运动）：deg + 90
        $trad = ($deg + 90) * [Math]::PI / 180.0
        $tx = [Math]::Cos($trad); $ty = [Math]::Sin($trad)
        # 法线方向
        $nx = -1 * [Math]::Sin($trad); $ny = [Math]::Cos($trad)
        $tipX = $px + $tx * $al
        $tipY = $py + $ty * $al
        $b1x = $px + $nx * $aw; $b1y = $py + $ny * $aw
        $b2x = $px - $nx * $aw; $b2y = $py - $ny * $aw
        $pts = @(
            (New-Object System.Drawing.PointF($tipX, $tipY)),
            (New-Object System.Drawing.PointF($b1x, $b1y)),
            (New-Object System.Drawing.PointF($b2x, $b2y))
        )
        $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
        $gg.FillPolygon($brush, $pts)
        $brush.Dispose()
    }

    # 箭头放在每段弧的末端
    $aw = $size * 0.055 * $contentScale
    $al = $size * 0.11 * $contentScale
    Add-Arrow $g $cx $cy $R 160 $aw $al   # 弧1末端 -100+260=160°
    Add-Arrow $g $cx $cy $R 340 $aw $al   # 弧2末端 80+260=340°

    $pen.Dispose()
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "saved $path"
}

# 标准 launcher 图标（含背景）
$densities = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($k in $densities.Keys) {
    $dir = Join-Path $res "mipmap-$k"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Draw-Icon $densities[$k] (Join-Path $dir "ic_launcher.png") 1.0
}

# 自适应图标前景（108dp 画布，内容缩进安全区 ~66%）
Draw-Icon 432 (Join-Path $res "drawable-nodpi\ic_launcher_fg.png") 0.62

Write-Host "All icons generated."
