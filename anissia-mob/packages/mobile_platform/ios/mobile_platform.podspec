Pod::Spec.new do |s|
  s.name             = 'mobile_platform'
  s.version          = '1.0.0'
  s.summary          = 'Native navigation and app links for Anissia.'
  s.homepage         = 'https://anissia.net'
  s.license          = { :type => 'Proprietary' }
  s.author           = { 'Anissia' => 'https://anissia.net' }
  s.source           = { :path => '.' }
  s.source_files     = 'mobile_platform/Sources/mobile_platform/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '18.4'
  s.swift_version = '5.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
