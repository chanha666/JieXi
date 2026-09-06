Add-Type -AssemblyName System.Drawing

$resourceDir = Join-Path $PSScriptRoot '..\desktopApp\src\main\resources'
$resourceDir = [System.IO.Path]::GetFullPath($resourceDir)
[System.IO.Directory]::CreateDirectory($resourceDir) | Out-Null

$size = 512
$bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::Transparent)

# Original open-frame extraction mark, identical geometry to Android jiexi_mark.xml.
$pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#B65326'), 32)
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$path = [System.Drawing.Drawing2D.GraphicsPath]::new()
$path.AddLine(168,96,128,96)
$path.AddBezier(128,96,96,96,80,112,80,144)
$path.AddLine(80,144,80,368)
$path.AddBezier(80,368,80,400,96,416,128,416)
$path.AddLine(128,416,384,416)
$path.AddBezier(384,416,416,416,432,400,432,368)
$path.AddLine(432,368,432,312)
$graphics.DrawPath($pen,$path)
$graphics.DrawLines($pen,[System.Drawing.PointF[]]@([System.Drawing.PointF]::new(344,96),[System.Drawing.PointF]::new(432,96),[System.Drawing.PointF]::new(432,184)))
$graphics.DrawLine($pen,256,104,256,320)
$graphics.DrawLines($pen,[System.Drawing.PointF[]]@([System.Drawing.PointF]::new(184,248),[System.Drawing.PointF]::new(256,320),[System.Drawing.PointF]::new(328,248)))

$pngPath = Join-Path $resourceDir 'icon.png'
$bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Save((Join-Path $resourceDir "icon_brand.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$composeIcon = Join-Path $PSScriptRoot "../desktopApp/src/main/composeResources/drawable/icon.png"
$bitmap.Save([IO.Path]::GetFullPath($composeIcon), [System.Drawing.Imaging.ImageFormat]::Png)

$iconPath = Join-Path $resourceDir 'icon.ico'
$iconSizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)
$iconImages = @()
foreach ($iconSize in $iconSizes) {
    $scaled = [System.Drawing.Bitmap]::new($iconSize, $iconSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $scaledGraphics = [System.Drawing.Graphics]::FromImage($scaled)
    $scaledGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $scaledGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $scaledGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $scaledGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $scaledGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $scaledGraphics.DrawImage($bitmap, 0, 0, $iconSize, $iconSize)
    $memory = [System.IO.MemoryStream]::new()
    $scaled.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
    $iconImages += ,$memory.ToArray()
    $memory.Dispose()
    $scaledGraphics.Dispose()
    $scaled.Dispose()
}

# Write a standards-compliant multi-resolution ICO. Icon.Save(GetHicon()) only
# emits a fragile single-size Windows handle and produced corrupted shell icons.
$iconStream = [System.IO.File]::Create($iconPath)
$writer = [System.IO.BinaryWriter]::new($iconStream)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$iconImages.Count)
$imageOffset = 6 + (16 * $iconImages.Count)
for ($index = 0; $index -lt $iconImages.Count; $index++) {
    $iconSize = $iconSizes[$index]
    $dimensionByte = if ($iconSize -eq 256) { [Byte]0 } else { [Byte]$iconSize }
    $writer.Write($dimensionByte)
    $writer.Write($dimensionByte)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$iconImages[$index].Length)
    $writer.Write([UInt32]$imageOffset)
    $imageOffset += $iconImages[$index].Length
}
foreach ($iconImage in $iconImages) { $writer.Write($iconImage) }
$writer.Dispose()
$iconStream.Dispose()

$path.Dispose()
$pen.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

Write-Output $pngPath
Write-Output $iconPath
